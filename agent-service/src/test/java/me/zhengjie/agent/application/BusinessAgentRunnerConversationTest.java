package me.zhengjie.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.guardrail.FinalAnswerGuardrail;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.presentation.PresentationRegistry;
import me.zhengjie.agent.presentation.PresentationService;
import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BusinessAgentRunner 的 Prompt 角色边界和会话 Patch 组装测试。 */
class BusinessAgentRunnerConversationTest {

    /** system prompt 只保留规则与受控摘要，不应包含当前用户问题正文。 */
    @Test
    void shouldKeepCurrentUserMessageOutOfSystemPrompt() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        BusinessAgentRunner runner = runner(objectMapper, registry);
        AgentChatRequest request = request("唯一用户问题正文-不要进入系统提示");

        String systemPrompt = runner.systemPrompt(request, registry.all());

        assertFalse(systemPrompt.contains(request.getMessage()));
    }

    /** 成功工具输入应让 Runner 返回完整的跨轮焦点和受控最近查询摘要。 */
    @Test
    void shouldAssembleConversationFocusFromSuccessfulFacts() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        BusinessAgentRunner runner = runner(objectMapper, registry);
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.LIST_MEAL_PLANS, "MEAL_PLAN_LIST",
            "{\"customerCode\":\"B3303\",\"recordDate\":\"2026-08-09\",\"mealType\":\"LUNCH\"}",
            "{\"items\":[]}", true);

        AgentChatResponse response = runner.runWithAnswer(request("查 B3303 今天午餐"), "已完成查询。", context);

        assertEquals("B3303", response.getSlots().getCustomerCode());
        assertEquals("2026-08-09", response.getSlots().getRecordDate());
        assertEquals("LUNCH", response.getSlots().getMealType());
        assertEquals("listMealPlans", response.getLastBusinessQueryContext().get("lastToolName"));
        assertEquals(List.of("listMealPlans"), response.getLastBusinessQueryContext().get("successfulToolNames"));
    }

    /** 无成功工具事实时必须保留请求中的上一版焦点和摘要。 */
    @Test
    void shouldPreservePreviousContextWhenRunHasNoSuccessfulFact() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        BusinessAgentRunner runner = runner(objectMapper, registry);
        DiagnosisSlots previous = new DiagnosisSlots();
        previous.setCustomerCode("B3303");
        previous.setOrderCode("O-1");
        previous.setMealType("LUNCH");
        Map<String, Object> previousSummary = Map.of("lastToolName", "listMealPlans", "partial", false);
        AgentChatRequest request = request("你好");
        request.setContextSlots(previous);
        request.setLastBusinessQueryContext(previousSummary);
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.LIST_MEAL_PLANS, "MEAL_PLAN_LIST", "{\"customerCode\":\"C9999\"}",
            "{\"errorCode\":\"ACCESS_DENIED\"}", false);

        AgentChatResponse response = runner.runWithAnswer(request, "你好。", context);

        assertEquals("B3303", response.getSlots().getCustomerCode());
        assertEquals("O-1", response.getSlots().getOrderCode());
        assertEquals("LUNCH", response.getSlots().getMealType());
        assertEquals(previousSummary, response.getLastBusinessQueryContext());
        assertNull(response.getSlots().getCustomerName());
    }

    /** 没有成功事实时，受控澄清仍可返回 NEED_MORE_INFO，且不会伪造实时查询结果。 */
    @Test
    void shouldReturnNeedMoreInfoWithoutSuccessfulFacts() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        BusinessAgentRunner runner = runner(objectMapper, registry);
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);

        AgentChatResponse response = runner.runWithAnswer(request("查一下排餐"),
            "{\"outcome\":\"NEED_MORE_INFO\",\"assistantMessage\":\"请补充客户编号。\",\"missingSlots\":[\"CUSTOMER_OR_ORDER\"]}",
            context);

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals(List.of(MissingSlot.CUSTOMER_OR_ORDER), response.getMissingSlots());
        assertTrue(response.getQuickReplies().isEmpty());
        assertEquals(0, context.successfulToolCalls());
    }

    /** 构造带固定系统展示服务的最小 Runner。 */
    private BusinessAgentRunner runner(ObjectMapper objectMapper, ToolRegistry registry) {
        SensitiveDataPolicy policy = new SensitiveDataPolicy();
        return new BusinessAgentRunner(null, null, registry, new FinalAnswerGuardrail(policy), policy,
            objectMapper, new AgentProperties(), null, new PresentationService(new PresentationRegistry(registry)));
    }

    /** 构造测试用聊天请求。 */
    private AgentChatRequest request(String message) {
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(message);
        request.setSessionId("session-conversation");
        request.setClientMessageId("message-conversation");
        return request;
    }
}
