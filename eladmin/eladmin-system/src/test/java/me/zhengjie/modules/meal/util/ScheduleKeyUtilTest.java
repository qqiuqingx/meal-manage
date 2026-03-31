package me.zhengjie.modules.meal.util;

import me.zhengjie.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleKeyUtilTest {

    @Test
    void shouldCalculateScheduleKey() {
        assertEquals("1-3", ScheduleKeyUtil.calcScheduleKey("2026-04-01"));
        assertEquals("2-3", ScheduleKeyUtil.calcScheduleKey("2026-04-08"));
        assertEquals("4-6", ScheduleKeyUtil.calcScheduleKey("2026-04-25"));
        assertEquals("1-3", ScheduleKeyUtil.calcScheduleKey("2026-04-29"));
    }

    @Test
    void shouldIdentifyWeekdayAndWeekend() {
        assertTrue(ScheduleKeyUtil.isWeekday(LocalDate.of(2026, 4, 1)));
        assertFalse(ScheduleKeyUtil.isWeekend(LocalDate.of(2026, 4, 1)));
        assertTrue(ScheduleKeyUtil.isWeekend(LocalDate.of(2026, 4, 25)));
        assertFalse(ScheduleKeyUtil.isWeekday(LocalDate.of(2026, 4, 25)));
    }

    @Test
    void shouldRejectInvalidDate() {
        assertThrows(BadRequestException.class, () -> ScheduleKeyUtil.parseDate(""));
        assertThrows(BadRequestException.class, () -> ScheduleKeyUtil.parseDate("2026/04/01"));
    }
}
