package me.zhengjie.agent.analyzer.impl;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlanFailedAnalyzerTest {

    @Test
    void shouldFlagPlanFailedWhenMealPlanStatusIsFailed() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");
        context.setMealPlan(Map.of(
            "status", "FAILED",
            "failReason", "候选菜过滤后为空"
        ));
        context.setCustomerPlans(List.of(Map.of(
            "status", 0,
            "failReason", "排除日期命中"
        )));

        PlanFailedAnalyzer analyzer = new PlanFailedAnalyzer();
        List<DiagnosisReasonDto> reasons = analyzer.analyze(context);

        assertFalse(reasons.isEmpty());
        assertEquals("PLAN_FAILED", reasons.get(0).getCode());
        assertEquals("排餐生成失败", reasons.get(0).getTitle());
    }
}
