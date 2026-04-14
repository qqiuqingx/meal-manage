/*
 *  Copyright 2019-2025 Zheng Jie
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package me.zhengjie.modules.customer.profile.domain.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CustomerProfileDetailDto 测试
 * @author qqx
 * @date 2026-04-14
 */
class CustomerProfileDetailDtoTest {

    @Test
    void testExcludedDatesFieldPresence() {
        CustomerProfileDetailDto dto = new CustomerProfileDetailDto();

        ExcludedDateDto dateDto = new ExcludedDateDto();
        dateDto.setDate("2026-04-15");
        dateDto.setMealTypes(Arrays.asList("BREAKFAST"));

        dto.setExcludedDates(Arrays.asList(dateDto));

        assertNotNull(dto.getExcludedDates());
        assertEquals(1, dto.getExcludedDates().size());
        assertEquals("2026-04-15", dto.getExcludedDates().get(0).getDate());
    }

    @Test
    void testExcludedDatesFromEntity() {
        ExcludedDateDto dateDto = new ExcludedDateDto();
        dateDto.setDate("2026-04-16");
        dateDto.setMealTypes(Arrays.asList("LUNCH", "DINNER"));

        CustomerProfileDetailDto dto = new CustomerProfileDetailDto();
        dto.setExcludedDates(Arrays.asList(dateDto));

        assertEquals("2026-04-16", dto.getExcludedDates().get(0).getDate());
        assertEquals(2, dto.getExcludedDates().get(0).getMealTypes().size());
        assertTrue(dto.getExcludedDates().get(0).getMealTypes().contains("LUNCH"));
        assertTrue(dto.getExcludedDates().get(0).getMealTypes().contains("DINNER"));
    }

    @Test
    void testExcludedDatesMultipleEntries() {
        ExcludedDateDto dto1 = new ExcludedDateDto();
        dto1.setDate("2026-04-20");
        dto1.setMealTypes(Arrays.asList("BREAKFAST", "LUNCH"));

        ExcludedDateDto dto2 = new ExcludedDateDto();
        dto2.setDate("2026-04-21");
        dto2.setMealTypes(Arrays.asList("DINNER"));

        CustomerProfileDetailDto dto = new CustomerProfileDetailDto();
        dto.setExcludedDates(Arrays.asList(dto1, dto2));

        assertEquals(2, dto.getExcludedDates().size());
        assertEquals("2026-04-20", dto.getExcludedDates().get(0).getDate());
        assertEquals("2026-04-21", dto.getExcludedDates().get(1).getDate());
    }
}
