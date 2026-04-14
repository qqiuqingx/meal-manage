/*
 *  Copyright 2019-2025 Zheng Jie
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package me.zhengjie.modules.customer.profile.domain.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CustomerProfileSaveDto 测试
 * @author qqx
 * @date 2026-04-14
 */
class CustomerProfileSaveDtoTest {

    @Test
    void testExcludedDatesFieldPresence() {
        CustomerProfileSaveDto dto = new CustomerProfileSaveDto();

        ExcludedDateDto dateDto = new ExcludedDateDto();
        dateDto.setDate("2026-04-15");
        dateDto.setMealTypes(Arrays.asList("BREAKFAST"));

        dto.setExcludedDates(Arrays.asList(dateDto));

        assertNotNull(dto.getExcludedDates());
        assertEquals(1, dto.getExcludedDates().size());
    }

    @Test
    void testExcludedDatesValidation() {
        ExcludedDateDto dateDto = new ExcludedDateDto();
        dateDto.setDate("2026-04-15");
        dateDto.setMealTypes(Arrays.asList("BREAKFAST"));

        CustomerProfileSaveDto dto = new CustomerProfileSaveDto();
        dto.setExcludedDates(Arrays.asList(dateDto));

        assertEquals("2026-04-15", dto.getExcludedDates().get(0).getDate());
        assertEquals(1, dto.getExcludedDates().get(0).getMealTypes().size());
        assertEquals("BREAKFAST", dto.getExcludedDates().get(0).getMealTypes().get(0));
    }

    @Test
    void testExcludedDatesAllMealTypes() {
        ExcludedDateDto dateDto = new ExcludedDateDto();
        dateDto.setDate("2026-04-18");
        dateDto.setMealTypes(Arrays.asList("BREAKFAST", "LUNCH", "DINNER"));

        CustomerProfileSaveDto dto = new CustomerProfileSaveDto();
        dto.setExcludedDates(Arrays.asList(dateDto));

        assertEquals(3, dto.getExcludedDates().get(0).getMealTypes().size());
        assertTrue(dto.getExcludedDates().get(0).getMealTypes().contains("BREAKFAST"));
        assertTrue(dto.getExcludedDates().get(0).getMealTypes().contains("LUNCH"));
        assertTrue(dto.getExcludedDates().get(0).getMealTypes().contains("DINNER"));
    }
}
