package me.zhengjie.agent.analyzer.impl;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OrderEffectiveAnalyzerTest {

    @Test
    void shouldFlagOrderAsNotEffectiveWhenStartDateIsAfterRecordDate() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");
        context.setOrders(List.of(Map.of(
            "orderCode", "ORD20260517001",
            "status", 1,
            "startDate", "2026-05-18",
            "endDate", "2026-06-30",
            "mealType", "ALL",
            "startMealType", "BREAKFAST"
        )));

        OrderEffectiveAnalyzer analyzer = new OrderEffectiveAnalyzer();
        List<DiagnosisReasonDto> reasons = analyzer.analyze(context);

        assertFalse(reasons.isEmpty());
        assertEquals("ORDER_NOT_EFFECTIVE", reasons.get(0).getCode());
        assertEquals("订单未在目标日期生效", reasons.get(0).getTitle());
    }
}
