package me.zhengjie.agent.service.impl;

import me.zhengjie.agent.analyzer.impl.ExcludeDateAnalyzer;
import me.zhengjie.agent.context.DiagnosisContextBuilder;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.orchestrator.MealPlanDiagnosisOrchestrator;
import me.zhengjie.agent.summary.TemplateDiagnosisSummaryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MealPlanDiagnosisServiceImplTest {

    @Test
    void shouldBuildContextAndReturnDiagnosisResult() {
        DiagnosisContextBuilder contextBuilder = request -> {
            DiagnosisContextDto context = new DiagnosisContextDto();
            context.setCustomerId(request.getCustomerId());
            context.setCustomerName("张三");
            context.setRecordDate(request.getRecordDate());
            context.setMealType(request.getMealType());
            context.setCustomerProfile(Map.of(
                "excludeDates", List.of(
                    Map.of("date", "2026-05-17", "mealTypes", List.of("LUNCH"))
                )
            ));
            return context;
        };

        MealPlanDiagnosisOrchestrator orchestrator = new MealPlanDiagnosisOrchestrator(
            List.of(new ExcludeDateAnalyzer()),
            new TemplateDiagnosisSummaryService()
        );
        MealPlanDiagnosisServiceImpl service = new MealPlanDiagnosisServiceImpl(contextBuilder, orchestrator);

        DiagnosisRequest request = new DiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        DiagnosisResponse response = service.diagnose(request);

        assertEquals("命中客户排除日期", response.getSummary());
        assertEquals(1, response.getReasons().size());
        assertEquals("张三", response.getCustomerName());
    }
}
