package me.zhengjie.agent.service.impl;

import me.zhengjie.agent.client.DiagnosisAiClient;
import me.zhengjie.agent.context.DiagnosisContextBuilder;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.orchestrator.MealPlanDiagnosisOrchestrator;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.rule.RuleRegistryLoader;
import org.junit.jupiter.api.Test;

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
            return context;
        };

        RuleRegistryLoader ruleRegistryLoader = scene -> {
            RuleRegistry registry = new RuleRegistry();
            registry.setScene(scene);
            return registry;
        };
        DiagnosisAiClient aiClient = (context, ruleRegistry) -> {
            DiagnosisResponse response = new DiagnosisResponse();
            response.setSummary("AI 判断命中客户排除日期");
            return response;
        };
        MealPlanDiagnosisOrchestrator orchestrator = new MealPlanDiagnosisOrchestrator(ruleRegistryLoader, aiClient);
        MealPlanDiagnosisServiceImpl service = new MealPlanDiagnosisServiceImpl(contextBuilder, orchestrator);

        DiagnosisRequest request = new DiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        DiagnosisResponse response = service.diagnose(request);

        assertEquals("AI 判断命中客户排除日期", response.getSummary());
        assertEquals("张三", response.getCustomerName());
    }
}
