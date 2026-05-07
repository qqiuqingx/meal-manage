package me.zhengjie.modules.customer.order.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStartMealTypeUtilTest {

    @Test
    void shouldReturnDefaultStartMealTypeByOrderMealType() {
        assertEquals("BREAKFAST", OrderStartMealTypeUtil.defaultStartMealType("ALL"));
        assertEquals("LUNCH", OrderStartMealTypeUtil.defaultStartMealType("LUNCH_DINNER"));
        assertEquals("LUNCH", OrderStartMealTypeUtil.defaultStartMealType("LUNCH"));
        assertEquals("DINNER", OrderStartMealTypeUtil.defaultStartMealType("DINNER"));
    }

    @Test
    void shouldValidateAllowedStartMealTypes() {
        assertTrue(OrderStartMealTypeUtil.isStartMealTypeAllowed("ALL", "DINNER"));
        assertTrue(OrderStartMealTypeUtil.isStartMealTypeAllowed("LUNCH_DINNER", "LUNCH"));
        assertFalse(OrderStartMealTypeUtil.isStartMealTypeAllowed("LUNCH_DINNER", "BREAKFAST"));
        assertFalse(OrderStartMealTypeUtil.isStartMealTypeAllowed("DINNER", "LUNCH"));
        assertEquals(Arrays.asList("LUNCH", "DINNER"), OrderStartMealTypeUtil.allowedStartMealTypes("LUNCH_DINNER"));
    }

    @Test
    void shouldRespectStartMealTypeOnStartDate() {
        LocalDate startDate = LocalDate.of(2026, 5, 7);
        assertFalse(OrderStartMealTypeUtil.hasStartedForMeal(startDate, "DINNER", startDate, "BREAKFAST"));
        assertFalse(OrderStartMealTypeUtil.hasStartedForMeal(startDate, "DINNER", startDate, "LUNCH"));
        assertTrue(OrderStartMealTypeUtil.hasStartedForMeal(startDate, "DINNER", startDate, "DINNER"));
        assertTrue(OrderStartMealTypeUtil.hasStartedForMeal(startDate, "DINNER", startDate.plusDays(1), "BREAKFAST"));
    }
}
