package me.zhengjie.agent.analyzer.impl;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScheduleModeAnalyzerTest {

    @Test
    void shouldFlagScheduleModeMismatchForWeekendWhenOrderIsWeekdayOnly() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setRecordDate("2026-05-16");
        context.setMealType("LUNCH");
        context.setOrders(List.of(Map.of(
            "orderCode", "ORD20260516001",
            "status", 1,
            "scheduleMode", "WEEKDAY"
        )));

        ScheduleModeAnalyzer analyzer = new ScheduleModeAnalyzer();
        List<DiagnosisReasonDto> reasons = analyzer.analyze(context);

        assertFalse(reasons.isEmpty());
        assertEquals("SCHEDULE_MODE_MISMATCH", reasons.get(0).getCode());
        assertEquals("排餐模式不匹配", reasons.get(0).getTitle());
    }
}
