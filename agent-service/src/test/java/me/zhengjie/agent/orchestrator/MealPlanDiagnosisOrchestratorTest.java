package me.zhengjie.agent.orchestrator;

import me.zhengjie.agent.client.DiagnosisAiClient;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.rule.RuleRegistryLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MealPlanDiagnosisOrchestratorTest {

    @Test
    void shouldLoadRulesAndDelegateDiagnosisToAiClient() {
        RuleRegistry registry = new RuleRegistry();
        registry.setScene("MEAL_PLAN_NOT_GENERATED");
        registry.setVersionDigest("digest-1");

        RuleRegistryLoader ruleRegistryLoader = scene -> registry;
        DiagnosisAiClient aiClient = (context, ruleRegistry) -> {
            assertEquals("MEAL_PLAN_NOT_GENERATED", ruleRegistry.getScene());
            DiagnosisResponse response = new DiagnosisResponse();
            response.setSummary("AI 判断命中客户排除日期");
            return response;
        };

        MealPlanDiagnosisOrchestrator orchestrator = new MealPlanDiagnosisOrchestrator(ruleRegistryLoader, aiClient);

        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setCustomerId(1001L);
        context.setCustomerName("张三");
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");

        DiagnosisResponse response = orchestrator.orchestrate(context);

        assertEquals(1001L, response.getCustomerId());
        assertEquals("张三", response.getCustomerName());
        assertEquals("2026-05-17", response.getRecordDate());
        assertEquals("LUNCH", response.getMealType());
        assertEquals("AI 判断命中客户排除日期", response.getSummary());
    }
}
