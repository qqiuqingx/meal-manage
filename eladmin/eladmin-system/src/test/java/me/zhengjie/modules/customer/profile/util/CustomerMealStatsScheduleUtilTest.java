package me.zhengjie.modules.customer.profile.util;

import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerMealStatsScheduleUtilTest {

    @Test
    void shouldBuildWeekdayScheduleFromStartMealAndExcludeDates() {
        CustomerOrder order = new CustomerOrder();
        order.setId(1L);
        order.setCustomerId(10L);
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(20);
        order.setRemainingCount(20);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setEndDate(LocalDate.of(2026, 4, 7));
        order.setStartMealType("LUNCH");
        order.setMealType("ALL");
        order.setScheduleMode("WEEKDAY");

        ExcludedDateDto excluded = new ExcludedDateDto();
        excluded.setDate("2026-04-02");
        excluded.setMealTypes(Arrays.asList("DINNER"));

        List<CustomerMealStatsScheduleUtil.ScheduleDay> days = CustomerMealStatsScheduleUtil.buildMonthScheduleDays(
                Arrays.asList(order), Arrays.asList(excluded), "2026-04", "LUNCH_DINNER");

        assertEquals(5, days.size());
        assertEquals("2026-04-01", days.get(0).getDate());
        assertEquals(Arrays.asList("LUNCH", "DINNER"), days.get(0).getMealTypes());
        assertEquals("2026-04-02", days.get(1).getDate());
        assertEquals(Arrays.asList("LUNCH"), days.get(1).getMealTypes());
        assertEquals("2026-04-03", days.get(2).getDate());
        assertEquals(Arrays.asList("LUNCH", "DINNER"), days.get(2).getMealTypes());
        assertEquals("2026-04-06", days.get(3).getDate());
        assertEquals(Arrays.asList("LUNCH", "DINNER"), days.get(3).getMealTypes());
        assertEquals("2026-04-07", days.get(4).getDate());
        assertEquals(Arrays.asList("LUNCH", "DINNER"), days.get(4).getMealTypes());
    }

    @Test
    void shouldRespectScheduledDeliveryDatesWithMealTypes() {
        CustomerOrder order = new CustomerOrder();
        order.setId(2L);
        order.setCustomerId(10L);
        order.setBreakfastCount(5);
        order.setLunchDinnerCount(5);
        order.setRemainingCount(10);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setEndDate(LocalDate.of(2026, 4, 30));
        order.setStartMealType("BREAKFAST");
        order.setMealType("ALL");
        order.setScheduleMode("SCHEDULE");
        order.setDeliveryDates("[{\"date\":\"2026-04-05\",\"mealTypes\":[\"BREAKFAST\",\"DINNER\"]},{\"date\":\"2026-05-01\",\"mealTypes\":[\"LUNCH\"]}]");

        List<CustomerMealStatsScheduleUtil.ScheduleDay> breakfastDays = CustomerMealStatsScheduleUtil.buildMonthScheduleDays(
                Arrays.asList(order), null, "2026-04", "BREAKFAST");
        List<CustomerMealStatsScheduleUtil.ScheduleDay> lunchDinnerDays = CustomerMealStatsScheduleUtil.buildMonthScheduleDays(
                Arrays.asList(order), null, "2026-04", "LUNCH_DINNER");

        assertEquals(1, breakfastDays.size());
        assertEquals("2026-04-05", breakfastDays.get(0).getDate());
        assertEquals(Arrays.asList("BREAKFAST"), breakfastDays.get(0).getMealTypes());
        assertEquals(1, lunchDinnerDays.size());
        assertEquals("2026-04-05", lunchDinnerDays.get(0).getDate());
        assertEquals(Arrays.asList("DINNER"), lunchDinnerDays.get(0).getMealTypes());
    }
}
