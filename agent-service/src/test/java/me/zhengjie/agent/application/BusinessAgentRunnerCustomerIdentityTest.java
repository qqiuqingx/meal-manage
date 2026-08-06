package me.zhengjie.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.guardrail.FinalAnswerGuardrail;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.guardrail.ToolGuardrailException;
import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** BusinessAgentRunner 只能把成功工具事实中的客户编号—姓名配对交给最终回答护栏。 */
class BusinessAgentRunnerCustomerIdentityTest {

    /** 回答只出现成功事实中的完整姓名时，Runner 必须阻断而不能依赖历史上下文放行。 */
    @Test
    void shouldRejectUnpairedCustomerNameFromToolFact() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        BusinessAgentRunner runner = new BusinessAgentRunner(
            null,
            null,
            registry,
            new FinalAnswerGuardrail(new SensitiveDataPolicy()),
            new SensitiveDataPolicy(), objectMapper, new AgentProperties());
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.SEARCH_CUSTOMER_PROFILES, "CUSTOMER_PROFILE_LIST", "{}",
            "{\"items\":[{\"customerCode\":\"C1001\",\"customerName\":\"张三\"}]}", true);
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("查询客户");

        assertThrows(ToolGuardrailException.class,
            () -> runner.runWithAnswer(request, "客户张三目前有订单。", context));
    }
}
