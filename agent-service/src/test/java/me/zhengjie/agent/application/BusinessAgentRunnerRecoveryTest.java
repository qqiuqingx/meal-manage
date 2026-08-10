package me.zhengjie.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.application.conversation.AssistantTurnResult;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.guardrail.FinalAnswerGuardrail;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.infrastructure.llm.FallbackModelExecutor;
import me.zhengjie.agent.presentation.PresentationRegistry;
import me.zhengjie.agent.presentation.PresentationService;
import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BusinessAgentRunner 回答校验失败后的错误码和结构化结果恢复测试。 */
class BusinessAgentRunnerRecoveryTest {

    /** 模型回答重复表格时，立即保留成功工具卡片并改用确定性摘要，不再重复调用模型和工具。 */
    @Test
    void shouldKeepStructuredResultsWhenAnswerRepairIsExhausted() {
        AgentProperties properties = new AgentProperties();
        StubModelExecutor executor = new StubModelExecutor(properties,
            "| 客户编号 | 姓名 |\n|---|---|\n| C1001 | 张三 |");
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        BusinessAgentRunner runner = runner(executor, properties, objectMapper, registry);
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.SEARCH_SERVICE_CUSTOMERS, "SERVICE_CUSTOMER_LIST", "{}",
            "{\"items\":[{\"customerCode\":\"C1001\",\"customerName\":\"张三\",\"orderCode\":\"O1\",\"orderTime\":\"2026-08-05T10:00:00\",\"status\":\"ACTIVE\",\"parentPackageName\":\"套餐\"}],\"warnings\":[]}", true);

        AssistantTurnResult turn = runner.invokeWithRepairs("测试提示", "查询客户", List.of(), context);
        AgentChatResponse response = runner.runWithAnswer(request("查询客户"), turn.assistantMessage(), context);

        assertEquals(1, executor.calls);
        assertEquals("查询已完成，详细结果见下方。", response.getAssistantMessage());
        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals(1, response.getCards().size());
        assertEquals(1, response.getPresentations().size());
        assertFalse(response.isPartial());
    }

    /** 无工具事实的回答校验失败必须保留真实护栏码，不能伪装成模型不可用。 */
    @Test
    void shouldNotClassifyAnswerValidationAsModelUnavailable() {
        AgentProperties properties = new AgentProperties();
        StubModelExecutor executor = new StubModelExecutor(properties, " ");
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        AgentChatRequest request = request("你好");
        request.setAvailableTools(List.of());

        AgentChatResponse response = runner(executor, properties, objectMapper, registry).run(request);

        assertEquals(ChatStatus.ERROR, response.getStatus());
        assertTrue(response.getWarnings().contains("ANSWER_EMPTY"));
        assertFalse(response.getWarnings().contains("MODEL_UNAVAILABLE"));
        assertEquals("本次查询未能完成，请缩小查询范围后重试。", response.getAssistantMessage());
    }

    /** 构造带系统展示服务的最小 Runner。 */
    private BusinessAgentRunner runner(FallbackModelExecutor executor, AgentProperties properties,
                                       ObjectMapper objectMapper, ToolRegistry registry) {
        SensitiveDataPolicy policy = new SensitiveDataPolicy();
        return new BusinessAgentRunner(executor, null, registry, new FinalAnswerGuardrail(policy), policy,
            objectMapper, properties, null, new PresentationService(new PresentationRegistry(registry)));
    }

    /** 构造最小聊天请求。 */
    private AgentChatRequest request(String message) {
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(message);
        request.setSessionId("session-1");
        request.setClientMessageId("message-1");
        return request;
    }

    /** 不访问真实模型的固定回答执行器。 */
    private static final class StubModelExecutor extends FallbackModelExecutor {
        private final String answer;
        private int calls;

        private StubModelExecutor(AgentProperties properties, String answer) {
            super(List.of(), properties);
            this.answer = answer;
        }

        /** 返回固定回答并统计模型调用次数。 */
        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(String profileName, Function<ChatClient, T> invocation) {
            calls++;
            return (T) answer;
        }
    }
}
