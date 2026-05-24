package me.zhengjie.modules.customer.profile.domain.dto;

import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CustomerMealScheduleAdjustmentDtoTest {

    @Test
    void shouldHoldExcludedDatesAndManualAdditions() {
        ExcludedDateDto excludedDate = new ExcludedDateDto();
        excludedDate.setDate("2026-05-24");
        excludedDate.setMealTypes(Arrays.asList("LUNCH"));

        CustomerMealScheduleAdditionDto addition = new CustomerMealScheduleAdditionDto();
        addition.setOrderId(100L);
        addition.setDate("2026-05-25");
        addition.setMealType("DINNER");
        addition.setRemark("临时加餐");

        CustomerMealScheduleAdjustmentRequest request = new CustomerMealScheduleAdjustmentRequest();
        request.setCustomerId(10L);
        request.setExcludedDates(Arrays.asList(excludedDate));
        request.setAdditions(Arrays.asList(addition));

        assertEquals(10L, request.getCustomerId());
        assertEquals("2026-05-24", request.getExcludedDates().get(0).getDate());
        assertEquals("DINNER", request.getAdditions().get(0).getMealType());
    }

    @Test
    void shouldHoldManualAdditionEntityFields() {
        CustomerMealScheduleAddition entity = new CustomerMealScheduleAddition();
        entity.setCustomerId(10L);
        entity.setOrderId(100L);
        entity.setRecordDate(LocalDate.of(2026, 5, 25));
        entity.setMealType("DINNER");
        entity.setDeleted(false);

        assertEquals(10L, entity.getCustomerId());
        assertEquals(LocalDate.of(2026, 5, 25), entity.getRecordDate());
        assertFalse(entity.getDeleted());
    }
}
