package me.zhengjie.modules.customer.profile.util;

import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleCellDto;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import me.zhengjie.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerMealStatsScheduleUtilTest {

    @Test
    void shouldOnlyScheduleFutureMealsAfterImportedHistoricalVerification() {
        CustomerOrder order = new CustomerOrder();
        order.setId(11L);
        order.setCustomerId(12L);
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(7);
        order.setImportedVerifiedCount(3);
        order.setVerifiedCount(3);
        order.setRemainingCount(4);
        order.setStartDate(LocalDate.of(2026, 9, 26));
        order.setEndDate(LocalDate.of(2026, 9, 30));
        order.setMealType("LUNCH_DINNER");
        order.setScheduleMode("DAILY");

        java.util.Map<String, Integer> quantities = CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Collections.emptyList(), LocalDate.of(2026, 9, 30));

        assertEquals(4, quantities.size());
        assertEquals(Integer.valueOf(1), quantities.get("2026-09-26#LUNCH"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-09-26#DINNER"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-09-27#LUNCH"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-09-27#DINNER"));
    }

    @Test
    void shouldNeverAllocateFutureMealPoolToImportedHistoryEvenIfOrderStartMovesEarlier() {
        CustomerOrder order = new CustomerOrder();
        order.setId(11L);
        order.setLunchDinnerCount(4);
        order.setImportedVerifiedCount(2);
        order.setStartDate(LocalDate.of(2026, 9, 20));
        order.setMealType("LUNCH_DINNER");
        order.setScheduleMode("DAILY");
        CustomerMealScheduleAddition lunch = new CustomerMealScheduleAddition();
        lunch.setOrderId(11L);
        lunch.setRecordDate(LocalDate.of(2026, 9, 20));
        lunch.setMealType("LUNCH");
        lunch.setQuantity(1);
        lunch.setRemark(CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK);
        CustomerMealScheduleAddition dinner = new CustomerMealScheduleAddition();
        dinner.setOrderId(11L);
        dinner.setRecordDate(LocalDate.of(2026, 9, 20));
        dinner.setMealType("DINNER");
        dinner.setQuantity(1);
        dinner.setRemark(CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK);

        java.util.Map<String, Integer> quantities = CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Arrays.asList(lunch, dinner), LocalDate.of(2026, 9, 21));

        assertEquals(2, quantities.size());
        assertFalse(quantities.containsKey("2026-09-20#LUNCH"));
        assertFalse(quantities.containsKey("2026-09-20#DINNER"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-09-21#LUNCH"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-09-21#DINNER"));
    }

    @Test
    void shouldNotAllocateFutureBalanceInUnrecordedMonthsBeforeImport() {
        CustomerOrder order = new CustomerOrder();
        order.setId(11L);
        order.setStartDate(LocalDate.of(2026, 6, 2));
        order.setImportDate(LocalDate.of(2026, 10, 1));
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(7);
        order.setImportedVerifiedCount(3);
        order.setRemainingCount(4);
        order.setMealType("LUNCH_DINNER");
        order.setScheduleMode("DAILY");

        java.util.Map<String, Integer> history = CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Collections.emptyList(), LocalDate.of(2026, 10, 1));
        java.util.Map<String, Integer> future = CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Collections.emptyList(), LocalDate.of(2026, 10, 3));

        assertTrue(history.isEmpty());
        assertTrue(CustomerMealStatsScheduleUtil.buildMonthMealScheduleCells(order, Collections.emptyList(),
                "2026-09", Collections.emptyList()).isEmpty());
        assertEquals(4, future.size());
        assertEquals(Integer.valueOf(1), future.get("2026-10-02#LUNCH"));
        assertEquals(Integer.valueOf(1), future.get("2026-10-02#DINNER"));
        assertEquals(Integer.valueOf(1), future.get("2026-10-03#LUNCH"));
        assertEquals(Integer.valueOf(1), future.get("2026-10-03#DINNER"));
    }

    @Test
    void shouldNotReapplyOriginalDinnerStartOnFirstDateAfterImportBoundary() {
        CustomerOrder order = new CustomerOrder();
        order.setId(11L);
        order.setStartDate(LocalDate.of(2026, 6, 2));
        order.setStartMealType("DINNER");
        order.setImportDate(LocalDate.of(2026, 10, 20));
        order.setLunchDinnerCount(4);
        order.setImportedVerifiedCount(2);
        order.setMealType("LUNCH_DINNER");
        order.setScheduleMode("DAILY");
        java.util.Map<String, Integer> quantities = CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Collections.emptyList(), LocalDate.of(2026, 10, 21));
        assertEquals(Integer.valueOf(1), quantities.get("2026-10-21#LUNCH"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-10-21#DINNER"));
    }

    @Test
    void shouldBuildQuantityCellsWithBaseExcludedAndOffScheduleValues() {
        CustomerOrder order = new CustomerOrder();
        order.setId(4L);
        order.setCustomerId(10L);
        order.setLunchDinnerCount(10);
        order.setSoupCount(1);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setEndDate(LocalDate.of(2026, 4, 2));
        order.setStartMealType("LUNCH");
        order.setMealType("ALL");
        order.setScheduleMode("SCHEDULE");
        order.setDeliveryDates("[{\"date\":\"2026-04-02\",\"mealTypes\":[\"LUNCH\"]}]");

        ExcludedDateDto excluded = new ExcludedDateDto();
        excluded.setDate("2026-04-02");
        excluded.setMealTypes(Arrays.asList("LUNCH"));

        List<me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleCellDto> cells =
                CustomerMealStatsScheduleUtil.buildMonthMealScheduleCells(order, Arrays.asList(excluded), "2026-04", Collections.emptyList());

        assertEquals(4, cells.size());
        assertEquals(0, cells.get(0).getBaseQuantity());
        assertEquals(0, cells.get(0).getQuantity());
        assertEquals("2026-04-02", cells.get(2).getDate());
        assertEquals("LUNCH", cells.get(2).getMealType());
        assertEquals(0, cells.get(2).getBaseQuantity());
        assertEquals(0, cells.get(2).getQuantity());
        assertTrue(cells.get(2).getCustomerExcluded());
        assertTrue(cells.get(2).getDefaultIncludesSoup());
    }

    @Test
    void shouldTreatZeroOverrideAsCancellationAndReleaseMealPoolForLaterDates() {
        CustomerOrder order = new CustomerOrder();
        order.setId(41L);
        order.setLunchDinnerCount(3);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setEndDate(LocalDate.of(2026, 4, 2));
        order.setMealType("LUNCH_DINNER");
        order.setStartMealType("LUNCH");
        order.setScheduleMode("DAILY");

        CustomerMealScheduleAddition cancellation = new CustomerMealScheduleAddition();
        cancellation.setOrderId(41L);
        cancellation.setRecordDate(LocalDate.of(2026, 4, 1));
        cancellation.setMealType("LUNCH");
        cancellation.setQuantity(0);

        java.util.Map<String, Integer> quantities = CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Collections.singletonList(cancellation), LocalDate.of(2026, 4, 2));

        assertEquals(Integer.valueOf(0), quantities.get("2026-04-01#LUNCH"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-04-01#DINNER"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-04-02#LUNCH"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-04-02#DINNER"));
    }

    @Test
    void shouldApplyHistoricalZeroOverrideBeforeAllocatingTheSelectedMonth() {
        CustomerOrder order = new CustomerOrder();
        order.setId(44L);
        order.setLunchDinnerCount(2);
        order.setStartDate(LocalDate.of(2026, 9, 30));
        order.setEndDate(LocalDate.of(2026, 10, 2));
        order.setMealType("LUNCH_DINNER");
        order.setStartMealType("LUNCH");
        order.setScheduleMode("DAILY");

        CustomerMealScheduleAddition historicalCancellation = new CustomerMealScheduleAddition();
        historicalCancellation.setOrderId(44L);
        historicalCancellation.setRecordDate(LocalDate.of(2026, 9, 30));
        historicalCancellation.setMealType("LUNCH");
        historicalCancellation.setQuantity(0);

        java.util.Map<String, Integer> quantities = CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Collections.singletonList(historicalCancellation), LocalDate.of(2026, 10, 1));

        assertEquals(Integer.valueOf(0), quantities.get("2026-09-30#LUNCH"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-09-30#DINNER"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-10-01#LUNCH"));
    }

    @Test
    void shouldKeepBreakfastPoolIndependentFromSharedLunchDinnerPoolWhenCancelled() {
        CustomerOrder order = new CustomerOrder();
        order.setId(45L);
        order.setBreakfastCount(1);
        order.setLunchDinnerCount(1);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setEndDate(LocalDate.of(2026, 4, 2));
        order.setMealType("ALL");
        order.setStartMealType("BREAKFAST");
        order.setScheduleMode("DAILY");

        CustomerMealScheduleAddition cancellation = new CustomerMealScheduleAddition();
        cancellation.setOrderId(45L);
        cancellation.setRecordDate(LocalDate.of(2026, 4, 1));
        cancellation.setMealType("BREAKFAST");
        cancellation.setQuantity(0);

        java.util.Map<String, Integer> quantities = CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Collections.singletonList(cancellation), LocalDate.of(2026, 4, 2));

        assertEquals(Integer.valueOf(0), quantities.get("2026-04-01#BREAKFAST"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-04-01#LUNCH"));
        assertEquals(Integer.valueOf(1), quantities.get("2026-04-02#BREAKFAST"));
        assertFalse(quantities.containsKey("2026-04-01#DINNER"));
        assertFalse(quantities.containsKey("2026-04-02#LUNCH"));
    }

    @Test
    void shouldExposeCustomerAndOrderExclusionsAsIndependentCellSources() {
        CustomerOrder order = new CustomerOrder();
        order.setId(42L);
        order.setLunchDinnerCount(2);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setEndDate(LocalDate.of(2026, 4, 1));
        order.setMealType("ALL");
        order.setStartMealType("LUNCH");
        order.setScheduleMode("DAILY");

        CustomerMealScheduleAddition cancellation = new CustomerMealScheduleAddition();
        cancellation.setOrderId(42L);
        cancellation.setRecordDate(LocalDate.of(2026, 4, 1));
        cancellation.setMealType("LUNCH");
        cancellation.setQuantity(0);

        ExcludedDateDto customerExclusion = new ExcludedDateDto();
        customerExclusion.setDate("2026-04-01");
        customerExclusion.setMealTypes(Collections.singletonList("DINNER"));

        List<CustomerMealScheduleCellDto> cells = CustomerMealStatsScheduleUtil.buildMonthMealScheduleCells(
                order, Collections.singletonList(customerExclusion), "2026-04", Collections.singletonList(cancellation));

        CustomerMealScheduleCellDto lunch = cells.get(0);
        assertEquals("LUNCH", lunch.getMealType());
        assertEquals(0, lunch.getQuantity());
        assertEquals(1, lunch.getBaseQuantity());
        assertTrue(lunch.getManualOverride());
        assertFalse(lunch.getCustomerExcluded());
        assertTrue(lunch.getOrderExcluded());

        CustomerMealScheduleCellDto dinner = cells.get(1);
        assertEquals("DINNER", dinner.getMealType());
        assertEquals(0, dinner.getQuantity());
        assertFalse(dinner.getManualOverride());
        assertTrue(dinner.getCustomerExcluded());
        assertFalse(dinner.getOrderExcluded());
    }

    @Test
    void shouldCalculateBaseQuantityWithEarlierMonthOverridesStillApplied() {
        CustomerOrder order = new CustomerOrder();
        order.setId(46L);
        order.setLunchDinnerCount(2);
        order.setStartDate(LocalDate.of(2026, 9, 30));
        order.setEndDate(LocalDate.of(2026, 10, 1));
        order.setMealType("LUNCH_DINNER");
        order.setStartMealType("LUNCH");
        order.setScheduleMode("DAILY");

        CustomerMealScheduleAddition historicalCancellation = new CustomerMealScheduleAddition();
        historicalCancellation.setOrderId(46L);
        historicalCancellation.setRecordDate(LocalDate.of(2026, 9, 30));
        historicalCancellation.setMealType("LUNCH");
        historicalCancellation.setQuantity(0);
        CustomerMealScheduleAddition currentCancellation = new CustomerMealScheduleAddition();
        currentCancellation.setOrderId(46L);
        currentCancellation.setRecordDate(LocalDate.of(2026, 10, 1));
        currentCancellation.setMealType("LUNCH");
        currentCancellation.setQuantity(0);

        List<CustomerMealScheduleCellDto> cells = CustomerMealStatsScheduleUtil.buildMonthMealScheduleCells(
                order, Collections.emptyList(), "2026-10", Arrays.asList(historicalCancellation, currentCancellation));
        CustomerMealScheduleCellDto lunch = cells.stream()
                .filter(cell -> "2026-10-01".equals(cell.getDate()) && "LUNCH".equals(cell.getMealType()))
                .findFirst().orElseThrow(AssertionError::new);

        assertEquals(1, lunch.getBaseQuantity());
        assertEquals(0, lunch.getQuantity());
        assertTrue(lunch.getOrderExcluded());
    }

    @Test
    void shouldRejectNegativeQuantityOverrides() {
        CustomerOrder order = new CustomerOrder();
        order.setId(43L);
        order.setLunchDinnerCount(2);
        order.setStartDate(LocalDate.of(2026, 4, 1));
        order.setEndDate(LocalDate.of(2026, 4, 1));
        order.setMealType("LUNCH_DINNER");
        order.setScheduleMode("DAILY");
        CustomerMealScheduleAddition invalid = new CustomerMealScheduleAddition();
        invalid.setOrderId(43L);
        invalid.setRecordDate(LocalDate.of(2026, 4, 1));
        invalid.setMealType("LUNCH");
        invalid.setQuantity(-1);

        assertThrows(BadRequestException.class, () -> CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Collections.singletonList(invalid), LocalDate.of(2026, 4, 1)));
    }
}
