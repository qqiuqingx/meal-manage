package me.zhengjie.agent.analyzer.impl;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExcludeDateAnalyzerTest {

    @Test
    void shouldHitExcludeDateReason() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");
        context.setCustomerProfile(Map.of(
            "excludeDates", List.of(
                Map.of("date", "2026-05-17", "mealTypes", List.of("LUNCH", "DINNER"))
            )
        ));

        ExcludeDateAnalyzer analyzer = new ExcludeDateAnalyzer();
        List<DiagnosisReasonDto> reasons = analyzer.analyze(context);

        assertFalse(reasons.isEmpty());
        assertEquals("EXCLUDE_DATE_HIT", reasons.get(0).getCode());
        assertEquals("命中客户排除日期", reasons.get(0).getTitle());
        assertEquals("排除日期", reasons.get(0).getEvidence().get(0).getLabel());
    }
}
