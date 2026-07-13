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

    @Test
    void shouldLabelScheduledMenuTotalAsScheduledDishes() {
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setRecordDate("2026-07-12");
        var response = factory.create("session-1", slots, Map.of(), "DIAGNOSED", "BUSINESS_QUERY_SCHEDULED_MENU",
            Map.of("recordDate", "2026-07-12", "total", 3, "groups", List.of(
                Map.of("mealTypeCode", "LUNCH", "mealTypeName", "午餐", "total", 2, "items", List.of()),
                Map.of("mealTypeCode", "DINNER", "mealTypeName", "晚餐", "total", 1, "items", List.of()))),
            "指定日期公共排期菜单（按餐次）：午餐暂无已配置菜品；晚餐暂无已配置菜品。", List.of());

        assertEquals(1, response.getFacts().size());
        assertEquals("排期菜品数", response.getFacts().get(0).getLabel());
        assertEquals("道", response.getFacts().get(0).getUnit());
        assertEquals("SCHEDULED_DISH_LIST", response.getFacts().get(0).getSourceType());
    }

    @Test
    void shouldAcceptSameBusinessDateWhenMealPlanUsesDateTimeFormat() {
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setRecordDate("2026-07-13"); slots.setMealType("LUNCH");
        var response = factory.create("session-1", slots, Map.of(), "DIAGNOSED", "BUSINESS_QUERY_MEAL_PLAN_ALLERGY",
            Map.of("scannedCount", 1, "items", List.of(Map.of("customerCode", "B3303", "customerMealPlanId", 89231L,
                "recordDate", "2026-07-13 00:00:00", "mealTypeCode", "LUNCH", "dishes", List.of(Map.of("dishName", "香菇滑鸡",
                    "allergyFiltered", true, "replaceReason", "ALLERGY", "allergyReasons", List.of("鸡肉"))))), "truncated", false),
            "指定日期和餐次的排餐中，以下客户存在实际过敏过滤：B3303：香菇滑鸡（过敏标签：鸡肉）。本次扫描 1 条排餐记录。", List.of());

        assertEquals("B3303", response.getFacts().get(0).getCustomerCode());
        assertEquals("89231", response.getFacts().get(0).getSourceRecordId());
        assertTrue(response.getWarnings().isEmpty());
        assertFalse(response.getInsightResult().isEmpty());
        assertTrue(response.getAssistantMessage().contains("F1"));
    }

    @Test
    void shouldHideResultWhenReturnedBusinessDateDiffersFromQueryPlan() {
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setRecordDate("2026-07-13"); slots.setMealType("LUNCH");
        var response = factory.create("session-1", slots, Map.of(), "DIAGNOSED", "BUSINESS_QUERY_MEAL_PLAN_ALLERGY",
            Map.of("scannedCount", 1, "items", List.of(Map.of("customerCode", "B3303", "customerMealPlanId", 89231L,
                "recordDate", "2026-07-14 00:00:00", "mealTypeCode", "LUNCH", "dishes", List.of(Map.of("dishName", "香菇滑鸡",
                    "allergyFiltered", true, "replaceReason", "ALLERGY", "allergyReasons", List.of("鸡肉"))))), "truncated", false),
            "指定日期和餐次的排餐中，以下客户存在实际过敏过滤：B3303：香菇滑鸡（过敏标签：鸡肉）。本次扫描 1 条排餐记录。", List.of());

        assertTrue(response.getInsightResult().isEmpty());
        assertTrue(response.getWarnings().contains("PLAN_RESULT_MISMATCH"));
    }
}
