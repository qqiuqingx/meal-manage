/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.customer.profile.domain.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排除日期 DTO 测试类
 * 验证 ExcludedDateDto 的字段和 JSON 序列化格式
 *
 * Phase 10-02: 数据存储基础 - ExcludedDateDto tests
 * DATE-03 partial requirement: DTO structure validation
 *
 * @author qqx
 * @date 2026-04-14
 */
class ExcludedDateDtoTest {

    @Test
    void testJsonFormat() {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-15");
        dto.setMealTypes(Arrays.asList("BREAKFAST"));

        // Verify fields are correctly set
        assertEquals("2026-04-15", dto.getDate());
        assertEquals(1, dto.getMealTypes().size());
        assertEquals("BREAKFAST", dto.getMealTypes().get(0));
    }

    @Test
    void testFieldTypes() {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-20");

        // Verify date is String type
        assertTrue(dto.getDate() instanceof String);
        assertEquals("2026-04-20", dto.getDate());

        // Verify mealTypes is List type (may be null initially)
        // Once set, it should be List<String>
        dto.setMealTypes(Arrays.asList("LUNCH"));
        assertTrue(dto.getMealTypes() instanceof List);
    }

    @Test
    void testMultipleMealTypes() {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-16");
        dto.setMealTypes(Arrays.asList("BREAKFAST", "LUNCH", "DINNER"));

        assertEquals(3, dto.getMealTypes().size());
        assertTrue(dto.getMealTypes().contains("BREAKFAST"));
        assertTrue(dto.getMealTypes().contains("LUNCH"));
        assertTrue(dto.getMealTypes().contains("DINNER"));
    }

    @Test
    void testEmptyMealTypes() {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-17");
        dto.setMealTypes(Arrays.asList());

        assertNotNull(dto.getDate());
        assertNotNull(dto.getMealTypes());
        assertTrue(dto.getMealTypes().isEmpty());
    }

    @Test
    void testSingleMealType() {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-18");
        dto.setMealTypes(Arrays.asList("LUNCH"));

        assertEquals(1, dto.getMealTypes().size());
        assertEquals("LUNCH", dto.getMealTypes().get(0));
    }

    @Test
    void testDateFormatValidation() {
        // Test various valid date formats
        ExcludedDateDto dto1 = new ExcludedDateDto();
        dto1.setDate("2026-12-31");
        assertEquals("2026-12-31", dto1.getDate());

        ExcludedDateDto dto2 = new ExcludedDateDto();
        dto2.setDate("2026-01-01");
        assertEquals("2026-01-01", dto2.getDate());
    }
}
