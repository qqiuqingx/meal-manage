package me.zhengjie.agent.query.tool;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.AgentQueryPlanValidator;
import me.zhengjie.agent.query.BusinessQueryPlanner;
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.client.BusinessQueryClientException;
import me.zhengjie.agent.security.AgentAccessContextHolder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBusinessToolExecutorTest {
    @Test
    void shouldReuseSameToolAndPlanWithinRound() {
        AtomicInteger calls = new AtomicInteger();
        AgentBusinessToolExecutor executor = new AgentBusinessToolExecutor(client(calls), new AgentQueryPlanValidator());
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode("B3303");
        var plan = new BusinessQueryPlanner().plan("BUSINESS_QUERY_CUSTOMER", slots);

        var first = executor.execute(plan, "customerOverview", null, List.of());
        var second = executor.execute(plan, "customerOverview", null, List.of());

        assertFalse(first.cached());
        assertTrue(second.cached());
        assertEquals(1, calls.get());
        assertEquals(1, executor.getCallCount());
    }

    @Test
    void shouldRejectUnregisteredToolBeforeCallingClient() {
        AtomicInteger calls = new AtomicInteger();
        AgentBusinessToolExecutor executor = new AgentBusinessToolExecutor(client(calls), new AgentQueryPlanValidator());
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode("B3303");
        var plan = new BusinessQueryPlanner().plan("BUSINESS_QUERY_CUSTOMER", slots);

        var result = executor.execute(plan, "notRegistered", null, List.of());

        assertTrue(result.partial());
        assertEquals(List.of("PLAN_INVALID"), result.warnings());
        assertEquals(0, calls.get());
    }

    @Test
    void shouldExecuteCustomerResolveToolForNameCandidates() {
        AtomicInteger calls = new AtomicInteger();
        AgentBusinessToolExecutor executor = new AgentBusinessToolExecutor(client(calls), new AgentQueryPlanValidator());
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerName("张三");
        var plan = new BusinessQueryPlanner().plan("BUSINESS_QUERY_CUSTOMER_CANDIDATES", slots);

        var result = executor.execute(plan, "resolveCustomer", null, List.of());

        assertFalse(result.partial());
        assertEquals(1, calls.get());
        assertEquals(2, ((Number) result.result().get("total")).intValue());
    }

    /** 主系统下发的工具白名单应在 Agent 编排前阻断无业务权限调用。 */
    @Test
    void shouldNotCallToolExcludedByCurrentOperatorPermissions() {
        AtomicInteger calls = new AtomicInteger();
        AgentBusinessToolExecutor executor = new AgentBusinessToolExecutor(client(calls), new AgentQueryPlanValidator());
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode("B3303");
        var plan = new BusinessQueryPlanner().plan("BUSINESS_QUERY_CUSTOMER", slots);
        AgentAccessContextHolder.bindAvailableTools(List.of("listOrders"));
        try {
            var result = executor.execute(plan, "customerOverview", null, List.of());

            assertTrue(result.partial());
            assertEquals(List.of("TOOL_PERMISSION_DENIED"), result.warnings());
            assertEquals(0, calls.get());
        } finally {
            AgentAccessContextHolder.clear();
        }
    }

    @Test
    void shouldPreserveStableInternalQueryFailureCode() {
        BusinessQueryDataClient failing = new BusinessQueryDataClient() {
            public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            public Map<String, Object> customerOverview(Long customerId, String customerCode) { throw new BusinessQueryClientException("AGENT_QUERY_ACCESS_DENIED"); }
            public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            public Map<String, Object> explainRule(String topic) { return Map.of(); }
            public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        AgentBusinessToolExecutor executor = new AgentBusinessToolExecutor(failing, new AgentQueryPlanValidator());
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setCustomerCode("B3303");

        var result = executor.execute(new BusinessQueryPlanner().plan("BUSINESS_QUERY_CUSTOMER", slots), "customerOverview", null, List.of());

        assertTrue(result.partial());
        assertEquals(List.of("AGENT_QUERY_ACCESS_DENIED"), result.warnings());
    }

    @Test
    void shouldStopBeforeToolCallsExceedRoundDataItemBudget() {
        AtomicInteger calls = new AtomicInteger();
        AgentBusinessToolExecutor executor = new AgentBusinessToolExecutor(client(calls), new AgentQueryPlanValidator());
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setCustomerId(3303L);
        var verificationPlan = new BusinessQueryPlanner().plan("BUSINESS_QUERY_VERIFICATION", slots);
        var refundPlan = new BusinessQueryPlanner().plan("BUSINESS_QUERY_REFUND", slots);
        verificationPlan.getFilters().setRecentLimit(50);
        refundPlan.getFilters().setRecentLimit(50);
        slots.setRecordDate("2026-05-22"); slots.setMealType("LUNCH");
        var dishPlan = new BusinessQueryPlanner().plan("BUSINESS_QUERY_DISH", slots);

        assertFalse(executor.execute(verificationPlan, "listVerifications", null, List.of()).partial());
        assertFalse(executor.execute(refundPlan, "listRefunds", null, List.of()).partial());
        var exceeded = executor.execute(dishPlan, "listDishes", null, List.of(1));

        assertTrue(exceeded.partial());
        assertEquals(List.of("TOOL_DATA_BUDGET_EXCEEDED"), exceeded.warnings());
        assertEquals(100, executor.getReservedDataItems());
    }

    private BusinessQueryDataClient client(AtomicInteger calls) {
        return new BusinessQueryDataClient() {
            public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { calls.incrementAndGet(); return Map.of("total", 2, "items", List.of()); }
            public Map<String, Object> customerOverview(Long customerId, String customerCode) { calls.incrementAndGet(); return Map.of("present", true, "customerCode", customerCode); }
            public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            public Map<String, Object> explainRule(String topic) { return Map.of(); }
            public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
    }
}
