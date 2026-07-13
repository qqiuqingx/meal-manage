package me.zhengjie.agent.query;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessQueryPlannerTest {
    private final BusinessQueryPlanner planner = new BusinessQueryPlanner();

    @Test
    void shouldBuildOrderDetailPlanWithOrderEntity() {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setOrderCode("O20260711001");

        var plan = planner.plan("BUSINESS_QUERY_ORDER", slots);

        assertEquals("O20260711001", plan.getEntities().getOrderCode());
        assertEquals(List.of("orderDetail"), plan.getToolNames());
    }

    @Test
    void shouldBuildDishPlanWithBothControlledTools() {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode("B3303");
        slots.setRecordDate("2026-07-11");

        var plan = planner.plan("BUSINESS_QUERY_DISH", slots);

        assertEquals(List.of("listMealPlans", "listDishes"), plan.getToolNames());
    }

    @Test
    void shouldBuildCustomerCandidatePlanWithCustomerName() {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerName("张三");

        var plan = planner.plan("BUSINESS_QUERY_CUSTOMER_CANDIDATES", slots);

        assertEquals("张三", plan.getEntities().getCustomerName());
        assertEquals(List.of("resolveCustomer"), plan.getToolNames());
    }
}
