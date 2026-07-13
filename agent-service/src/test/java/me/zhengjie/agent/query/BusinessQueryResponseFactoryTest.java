package me.zhengjie.agent.query;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证业务查询响应工厂只输出可追溯事实和受控 QueryPlan。 */
class BusinessQueryResponseFactoryTest {
    private final BusinessQueryResponseFactory factory = new BusinessQueryResponseFactory(new BusinessAnswerValidator());

    @Test
    void shouldBuildCandidateFactsAndControlledPlan() {
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setCustomerId(3303L); slots.setRecordDate("2026-07-12"); slots.setMealType("LUNCH");
        var response = factory.create("session-1", slots, Map.of(), "DIAGNOSED", "BUSINESS_QUERY_DISH_CANDIDATES",
            Map.of("present", true, "customerId", 3303L, "totalCandidateCount", 4, "availableCandidateCount", 2, "filteredCandidateCount", 2),
            "指定日期餐次共有 4 个排期候选菜，其中 2 个当前可用、2 个因套餐、客户排除菜或过敏标签被过滤。", List.of());

        assertEquals(3, response.getFacts().size());
        assertEquals("DISH", response.getQueryPlan().getDomain().name());
        assertTrue(response.getAssistantMessage().contains("F1"));
    }

    @Test
    void shouldHideResultWhenTemplateContainsUnfoundedQuantity() {
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setCustomerId(3303L); slots.setRecordDate("2026-07-12"); slots.setMealType("LUNCH");
        var response = factory.create("session-1", slots, Map.of(), "DIAGNOSED", "BUSINESS_QUERY_DISH_CANDIDATES",
            Map.of("present", true, "customerId", 3303L, "totalCandidateCount", 4, "availableCandidateCount", 2, "filteredCandidateCount", 2),
            "指定日期餐次共有 5 个排期候选菜，其中 2 个当前可用、2 个因套餐过滤。", List.of());

        assertTrue(response.getInsightResult().isEmpty());
        assertFalse(response.getWarnings().isEmpty());
    }

    @Test
    void shouldHideResultWhenReturnedCustomerDiffersFromQueryPlan() {
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setCustomerId(3303L);
        var response = factory.create("session-1", slots, Map.of(), "DIAGNOSED", "BUSINESS_QUERY_CUSTOMER",
            Map.of("present", true, "customerId", 9999L, "activeOrderCount", 0, "mealBalance", Map.of("remainingBreakfast", 0, "remainingLunchDinner", 0)),
            "B3303（客户）当前有 0 笔进行中订单，剩余早餐 0 餐、午晚餐 0 餐。", List.of());

        assertTrue(response.getInsightResult().isEmpty());
        assertFalse(response.getWarnings().isEmpty());
        assertTrue(response.isPartial());
        assertTrue(response.getWarnings().contains("PLAN_RESULT_MISMATCH"));
    }

    @Test
    void shouldBuildOneFactPerControlledOperationReportMetric() {
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setRecordDate("2026-07-12"); slots.setMealType("LUNCH");
        var response = factory.create("session-1", slots, Map.of(), "DIAGNOSED", "BUSINESS_QUERY_OPERATION_REPORT",
            Map.of("recordDate", "2026-07-12", "scheduledCustomerCount", 4, "unverifiedCustomerCount", 2,
                "reportMetrics", List.of("DAILY_SCHEDULED_CUSTOMER_COUNT", "DAILY_UNVERIFIED_CUSTOMER_COUNT"),
                "metricDefinitionId", "AGENT_DAILY_CUSTOMER_WORKLOAD_V1"),
            "2026-07-12运营统计：已排餐客户数 4 个；待核销客户数 2 个。统计口径：AGENT_DAILY_CUSTOMER_WORKLOAD_V1。", List.of());

        assertEquals(2, response.getFacts().size());
        assertEquals("已排餐客户数", response.getFacts().get(0).getLabel());
        assertEquals("待核销客户数", response.getFacts().get(1).getLabel());
        assertTrue(response.getAssistantMessage().contains("F1"));
        assertTrue(response.getAssistantMessage().contains("F2"));
    }
}
