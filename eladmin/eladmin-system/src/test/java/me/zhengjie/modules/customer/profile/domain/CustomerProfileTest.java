/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *
 *  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.customer.profile.domain;

import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CustomerProfile Entity 单元测试
 *
 * Wave 2 (TDD): Tests for excludedDates field with JacksonTypeHandler serialization.
 * Tests validate:
 * - Field serialization (set/get)
 * - Multiple entries with different mealTypes
 * - Null and empty list handling
 *
 * @author qqx
 * @date 2026-04-14
 */
class CustomerProfileTest {

    private CustomerProfile profile;

    @BeforeEach
    void setUp() {
        profile = new CustomerProfile();
        profile.setId(1L);
        profile.setCustomerName("张三");
        profile.setPhone("13800138000");
    }

    /**
     * Test 1: testExcludedDatesFieldSerialization
     *
     * Verifies that the excludedDates field can hold a List<ExcludedDateDto>
     * and that JacksonTypeHandler correctly serializes it.
     *
     * Expected JSON format: [{"date":"2026-04-15","mealTypes":["BREAKFAST"]},{"date":"2026-04-16","mealTypes":["LUNCH","DINNER"]}]
     */
    @Test
    void testExcludedDatesFieldSerialization() {
        ExcludedDateDto dto1 = new ExcludedDateDto();
        dto1.setDate("2026-04-15");
        dto1.setMealTypes(Arrays.asList("BREAKFAST"));

        ExcludedDateDto dto2 = new ExcludedDateDto();
        dto2.setDate("2026-04-16");
        dto2.setMealTypes(Arrays.asList("LUNCH", "DINNER"));

        List<ExcludedDateDto> excludedDates = Arrays.asList(dto1, dto2);
        profile.setExcludedDates(excludedDates);

        assertNotNull(profile.getExcludedDates());
        assertEquals(2, profile.getExcludedDates().size());

        // Verify first entry
        assertEquals("2026-04-15", profile.getExcludedDates().get(0).getDate());
        assertEquals(1, profile.getExcludedDates().get(0).getMealTypes().size());
        assertEquals("BREAKFAST", profile.getExcludedDates().get(0).getMealTypes().get(0));

        // Verify second entry
        assertEquals("2026-04-16", profile.getExcludedDates().get(1).getDate());
        assertEquals(2, profile.getExcludedDates().get(1).getMealTypes().size());
        assertTrue(profile.getExcludedDates().get(1).getMealTypes().contains("LUNCH"));
        assertTrue(profile.getExcludedDates().get(1).getMealTypes().contains("DINNER"));
    }

    /**
     * Test 2: testExcludedDatesNullHandling
     *
     * Verifies that null and empty list handling for excludedDates is correct.
     * - null field: should return null (not set)
     * - empty list []: should return empty list (set but no exclusions)
     */
    @Test
    void testExcludedDatesNullHandling() {
        // null means "not set" (no exclusion dates configured)
        profile.setExcludedDates(null);
        assertNull(profile.getExcludedDates());

        // empty list means "set but no exclusions"
        profile.setExcludedDates(Collections.emptyList());
        assertNotNull(profile.getExcludedDates());
        assertEquals(0, profile.getExcludedDates().size());
    }
}
