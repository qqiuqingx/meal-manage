package me.zhengjie.agent.orchestrator;

import me.zhengjie.agent.analyzer.DiagnosisAnalyzer;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.summary.DiagnosisSummaryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MealPlanDiagnosisOrchestratorTest {

    @Test
    void shouldCollectReasonsAndBuildSummary() {
        DiagnosisAnalyzer analyzer = context -> List.of(buildReason("EXCLUDE_DATE_HIT", "命中客户排除日期"));
        DiagnosisSummaryService summaryService = response -> "客户 1001 命中排除日期";

        MealPlanDiagnosisOrchestrator orchestrator =
            new MealPlanDiagnosisOrchestrator(List.of(analyzer), summaryService);

        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setCustomerId(1001L);
        context.setCustomerName("张三");
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");

        DiagnosisResponse response = orchestrator.orchestrate(context);

        assertEquals(1001L, response.getCustomerId());
        assertEquals(1, response.getReasons().size());
        assertEquals("客户 1001 命中排除日期", response.getSummary());
    }

    private DiagnosisReasonDto buildReason(String code, String title) {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode(code);
        reason.setTitle(title);
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("排除日期", "2026-05-17")));
        return reason;
    }
}
