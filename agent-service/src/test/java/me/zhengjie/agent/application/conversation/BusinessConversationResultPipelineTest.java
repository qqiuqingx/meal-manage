package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.chat.MealPlanChatSession;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.BusinessQueryPlanner;
import me.zhengjie.agent.query.domain.BusinessResponseTypeCatalog;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.presentation.BusinessPresentationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证业务结果焦点和审计摘要统一由结果管线生成。 */
class BusinessConversationResultPipelineTest {

    private final BusinessConversationResultPipeline pipeline =
        new BusinessConversationResultPipeline();

    @Test
    void shouldCaptureStableOrderFocusFromTypedPresentation() {
        MealPlanChatSession session = new MealPlanChatSession();
        session.setSessionId("session-1");
        BusinessPresentationResult result = BusinessPresentationResult.fromPresentationMap(
            Map.of("present", true, "customerId", 64L, "customerCode", "A001",
                "items", List.of(Map.of("orderId", 81L, "orderCode", "O-81"))));

        pipeline.captureBusinessFocus(session, BusinessResponseTypeCatalog.ORDER, result);

        assertEquals(64L, session.getSlots().getCustomerId());
        assertEquals("A001", session.getSlots().getCustomerCode());
        assertEquals(81L, session.getSlots().getOrderId());
        assertEquals("O-81", session.getSlots().getOrderCode());
    }

    @Test
    void shouldPersistOnlyControlledScheduledMenuShape() {
        MealPlanChatSession session = new MealPlanChatSession();
        session.setSessionId("session-1");
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setRecordDate("2026-07-29");
        AgentChatResponse response = new AgentChatResponse();
        response.setResponseType(BusinessResponseTypeCatalog.SCHEDULED_MENU);
        response.setAssistantMessage("今日菜单");
        response.setQueryPlan(new BusinessQueryPlanner().plan(
            BusinessResponseTypeCatalog.SCHEDULED_MENU, slots));
        response.setInsightResult(Map.of("total", 2,
            "groups", List.of(Map.of("mealTypeCode", "LUNCH", "total", 2,
                "items", List.of(Map.of("dishTypeCode", "MEAT"),
                    Map.of("dishTypeCode", "VEGETABLE"))))));

        pipeline.captureLastBusinessQueryContext(session, response);

        var context = session.getConversationState().getLastBusinessQueryContext();
        assertNotNull(context);
        assertEquals("2026-07-29", context.getRecordDate());
        assertEquals(Map.of("LUNCH", 2), context.getResultShape().get("mealTypes"));
        assertEquals(Map.of("MEAT", 1, "VEGETABLE", 1),
            context.getResultShape().get("dishTypeDistribution"));
        assertEquals(context, response.getLastBusinessQueryContext());
    }

    /** 订单统计上下文只能保存可重算集合句柄，不保存完整订单 ID 列表。 */
    @Test
    void shouldPersistRecomputableActiveOrderSetHandle() {
        MealPlanChatSession session = new MealPlanChatSession();
        session.setSessionId("session-1");
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V2);
        plan.setDomain(AgentQueryDomain.OPERATION_STATISTICS);
        plan.setAction(AgentQueryAction.SUMMARY);
        plan.setMetrics(List.of(AgentQueryMetric.ACTIVE_ORDER_COUNT));
        plan.setToolNames(List.of("getActiveOrderSummary"));
        AgentChatResponse response = new AgentChatResponse();
        response.setResponseType(AgentMetricCatalog.definition(
            AgentQueryMetric.ACTIVE_ORDER_COUNT).getResponseType());
        response.setQueryPlan(plan);
        response.setInsightResult(Map.of("present", true, "total", 15));

        pipeline.captureLastBusinessQueryContext(session, response);

        var context = response.getLastBusinessQueryContext();
        assertNotNull(context);
        assertEquals(1, context.getContextHandles().size());
        assertEquals("AGENT_ACTIVE_ORDER_V1", context.getContextHandles().get(0).getDefinitionId());
        assertEquals(15, context.getContextHandles().get(0).getCardinality());
        assertEquals(Map.of("metric", "ACTIVE_ORDER_COUNT", "status", 1),
            context.getContextHandles().get(0).getSafeDescriptor());
    }
}
