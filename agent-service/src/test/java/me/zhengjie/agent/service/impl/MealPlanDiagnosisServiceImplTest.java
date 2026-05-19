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
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealPlanDiagnosisServiceImplTest {

    @Test
    void shouldBuildLightweightContextAndReturnDiagnosisResult() {
        DiagnosisContextBuilder contextBuilder = request -> {
            throw new AssertionError("context builder should not be called in tool mode");
        };

        RuleRegistryLoader ruleRegistryLoader = scene -> {
            RuleRegistry registry = new RuleRegistry();
            registry.setScene(scene);
            return registry;
        };
        DiagnosisAiClient aiClient = (context, ruleRegistry) -> {
            assertEquals(1001L, context.getCustomerId());
            assertEquals("C1001", context.getCustomerCode());
            assertEquals("2026-05-17", context.getRecordDate());
            assertEquals("LUNCH", context.getMealType());
            assertTrue(context.getOrders().isEmpty());
            assertTrue(context.getCustomerPlans().isEmpty());
            assertTrue(context.getCandidateDishStats().isEmpty());
            assertTrue(context.getCustomerProfile().isEmpty());
            assertTrue(context.getMealPlan().isEmpty());
            DiagnosisResponse response = new DiagnosisResponse();
            response.setSummary("AI 判断命中客户排除日期");
            return response;
        };
        MealPlanDiagnosisOrchestrator orchestrator = new MealPlanDiagnosisOrchestrator(ruleRegistryLoader, aiClient);
        MealPlanDiagnosisServiceImpl service = new MealPlanDiagnosisServiceImpl(contextBuilder, orchestrator);

        DiagnosisRequest request = new DiagnosisRequest();
        request.setCustomerId(1001L);
        request.setCustomerCode("C1001");
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        DiagnosisResponse response = service.diagnose(request);

        assertEquals("AI 判断命中客户排除日期", response.getSummary());
        assertEquals(1001L, response.getCustomerId());
        assertEquals("2026-05-17", response.getRecordDate());
        assertEquals("LUNCH", response.getMealType());
    }

    @Test
    void shouldCopyRequestIdFromLogContextIntoResponse() {
        MDC.put("requestId", "trace-1001");
        try {
            DiagnosisContextBuilder contextBuilder = request -> {
                throw new AssertionError("context builder should not be called in tool mode");
            };

            RuleRegistryLoader ruleRegistryLoader = scene -> new RuleRegistry();
            DiagnosisAiClient aiClient = (context, ruleRegistry) -> new DiagnosisResponse();
            MealPlanDiagnosisOrchestrator orchestrator = new MealPlanDiagnosisOrchestrator(ruleRegistryLoader, aiClient);
            MealPlanDiagnosisServiceImpl service = new MealPlanDiagnosisServiceImpl(contextBuilder, orchestrator);

            DiagnosisRequest request = new DiagnosisRequest();
            request.setCustomerId(1001L);
            request.setRecordDate("2026-05-17");
            request.setMealType("LUNCH");

            DiagnosisResponse response = service.diagnose(request);

            assertEquals("trace-1001", response.getRequestId());
        } finally {
            MDC.clear();
        }
    }
}
