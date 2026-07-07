package me.zhengjie.agent.evaluation;

import me.zhengjie.agent.client.DiagnosisAiClient;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.orchestrator.MealPlanDiagnosisOrchestrator;
import me.zhengjie.agent.rule.DiagnosisRule;
import me.zhengjie.agent.rule.FileSystemRuleRegistryLoader;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.rule.RuleRegistryLoader;
import me.zhengjie.agent.validator.DiagnosisResultValidator;
import me.zhengjie.agent.validator.DiagnosisValidationError;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealPlanDiagnosisEvaluationTest {

    private final RuleRegistryLoader ruleRegistryLoader = new FileSystemRuleRegistryLoader("rules");
    private final RuleRegistry ruleRegistry = ruleRegistryLoader.load("MEAL_PLAN_NOT_GENERATED");
    private final DiagnosisResultValidator validator = new DiagnosisResultValidator();

    @Test
    void shouldEvaluateMealPlanDiagnosisCasesThroughOrchestratorWithMockToolEvidence() {
        List<EvaluationCase> cases = loadCases();
        MockToolDataCatalog toolDataCatalog = new MockToolDataCatalog();
        MockDiagnosisAiClient aiClient = new MockDiagnosisAiClient(cases, toolDataCatalog, validator);
        MealPlanDiagnosisOrchestrator orchestrator = new MealPlanDiagnosisOrchestrator(ruleRegistryLoader, aiClient);

        assertEquals(101, cases.size());
        for (EvaluationCase evaluationCase : cases) {
            DiagnosisContextDto context = contextOf(evaluationCase);
            DiagnosisResponse response = orchestrator.orchestrate(context);
            List<DiagnosisValidationError> validationErrors = validator.validate(response, ruleRegistry);

            if (evaluationCase.allowFallback()) {
                assertTrue(response.isFallback(), evaluationCase.caseId());
                assertTrue(hasText(response.getFallbackReason()), evaluationCase.caseId());
                assertContainsAll(response.getNextActions(), evaluationCase.expectedNextActions(), evaluationCase.caseId());
                assertEquals(ruleRegistry.getVersionDigest(), response.getRuleVersionDigest(), evaluationCase.caseId());
                continue;
            }

            assertTrue(validationErrors.isEmpty(), evaluationCase.caseId() + " validationErrors=" + validationErrors);
            assertFalse(response.isFallback(), evaluationCase.caseId());
            assertEquals(context.getCustomerId(), response.getCustomerId(), evaluationCase.caseId());
            assertEquals(context.getRecordDate(), response.getRecordDate(), evaluationCase.caseId());
            assertEquals(context.getMealType(), response.getMealType(), evaluationCase.caseId());
            assertEquals(ruleRegistry.getVersionDigest(), response.getRuleVersionDigest(), evaluationCase.caseId());

            DiagnosisReasonDto matchedReason = findReason(response, evaluationCase.expectedReasonCode())
                .orElseThrow(() -> new AssertionError(evaluationCase.caseId() + " missing reason " + evaluationCase.expectedReasonCode()));

            assertContainsAll(matchedReason.getRuleIds(), evaluationCase.expectedRuleIds(), evaluationCase.caseId());
            assertContainsAll(evidenceLabels(matchedReason), evaluationCase.expectedEvidenceFields(), evaluationCase.caseId());
            assertContainsAll(matchedReason.getNextActions(), evaluationCase.expectedNextActions(), evaluationCase.caseId());
            assertContainsAll(response.getNextActions(), evaluationCase.expectedNextActions(), evaluationCase.caseId());
            assertTrue(toolDataCatalog.toolEvidence(evaluationCase).keySet().containsAll(evaluationCase.expectedEvidenceFields()),
                evaluationCase.caseId() + " mock tool evidence missing expected fields");
        }
    }

    @SuppressWarnings("unchecked")
    private List<EvaluationCase> loadCases() {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("evaluation/meal-plan-diagnosis-cases.yaml")) {
            assertNotNull(inputStream);
            Map<String, Object> root = new Yaml().load(inputStream);
            List<Map<String, Object>> rawCases = (List<Map<String, Object>>) root.get("cases");
            return rawCases.stream().map(this::toEvaluationCase).toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load meal plan diagnosis evaluation cases", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private EvaluationCase toEvaluationCase(Map<String, Object> raw) {
        return new EvaluationCase(
            asString(raw.get("caseId")),
            asString(raw.get("userInput")),
            (Map<String, Object>) raw.getOrDefault("slots", Map.of()),
            asString(raw.get("expectedReasonCode")),
            asStringList(raw.get("expectedRuleIds")),
            asStringList(raw.get("expectedEvidenceFields")),
            asStringList(raw.get("expectedNextActions")),
            Boolean.TRUE.equals(raw.get("allowFallback")),
            asString(raw.get("note"))
        );
    }

    private DiagnosisContextDto contextOf(EvaluationCase evaluationCase) {
        DiagnosisContextDto context = new DiagnosisContextDto();
        Object customerId = evaluationCase.slots().get("customerId");
        if (customerId instanceof Number number) {
            context.setCustomerId(number.longValue());
        }
        context.setCustomerCode(asString(evaluationCase.slots().get("customerCode")));
        context.setRecordDate(asString(evaluationCase.slots().get("recordDate")));
        context.setMealType(asString(evaluationCase.slots().get("mealType")));
        return context;
    }

    private Optional<DiagnosisReasonDto> findReason(DiagnosisResponse response, String expectedReasonCode) {
        if (response.getReasons() == null) {
            return Optional.empty();
        }
        return response.getReasons().stream()
            .filter(Objects::nonNull)
            .filter(reason -> expectedReasonCode.equals(reason.getCode()))
            .findFirst();
    }

    private Set<String> evidenceLabels(DiagnosisReasonDto reason) {
        if (reason.getEvidence() == null) {
            return Set.of();
        }
        return reason.getEvidence().stream()
            .map(DiagnosisEvidenceDto::getLabel)
            .collect(Collectors.toSet());
    }

    private void assertContainsAll(List<String> actual, List<String> expected, String caseId) {
        assertTrue(actual != null && actual.containsAll(expected),
            caseId + " expected=" + expected + " actual=" + actual);
    }

    private void assertContainsAll(Set<String> actual, List<String> expected, String caseId) {
        assertTrue(actual != null && actual.containsAll(expected),
            caseId + " expected=" + expected + " actual=" + actual);
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record EvaluationCase(String caseId,
                                  String userInput,
                                  Map<String, Object> slots,
                                  String expectedReasonCode,
                                  List<String> expectedRuleIds,
                                  List<String> expectedEvidenceFields,
                                  List<String> expectedNextActions,
                                  boolean allowFallback,
                                  String note) {
    }

    private static class MockToolDataCatalog {

        Map<String, String> toolEvidence(EvaluationCase evaluationCase) {
            Map<String, String> evidence = new LinkedHashMap<>();
            for (String field : evaluationCase.expectedEvidenceFields()) {
                evidence.put(field, valueFor(evaluationCase, field));
            }
            return evidence;
        }

        private String valueFor(EvaluationCase evaluationCase, String field) {
            return switch (field) {
                case "customerId" -> String.valueOf(evaluationCase.slots().getOrDefault("customerId", "-"));
                case "customerCode" -> String.valueOf(evaluationCase.slots().getOrDefault("customerCode", "-"));
                default -> field + "=" + evaluationCase.caseId();
            };
        }
    }

    private static class MockDiagnosisAiClient implements DiagnosisAiClient {

        private final Map<CaseKey, EvaluationCase> casesByKey;
        private final MockToolDataCatalog toolDataCatalog;
        private final DiagnosisResultValidator validator;

        MockDiagnosisAiClient(List<EvaluationCase> cases,
                              MockToolDataCatalog toolDataCatalog,
                              DiagnosisResultValidator validator) {
            this.casesByKey = cases.stream()
                .collect(Collectors.toMap(evaluationCase -> CaseKey.from(evaluationCase.slots()), evaluationCase -> evaluationCase));
            this.toolDataCatalog = toolDataCatalog;
            this.validator = validator;
        }

        @Override
        public DiagnosisResponse diagnose(DiagnosisContextDto context, RuleRegistry ruleRegistry) {
            EvaluationCase evaluationCase = casesByKey.get(CaseKey.from(context));
            assertNotNull(evaluationCase, "missing evaluation case for context " + CaseKey.from(context));
            DiagnosisResponse response;
            if (evaluationCase.allowFallback()) {
                response = fallbackResponse(evaluationCase);
            } else {
                response = diagnosisResponse(evaluationCase, ruleRegistry);
            }
            return validator.validateOrFallback(response, context, ruleRegistry);
        }

        private DiagnosisResponse diagnosisResponse(EvaluationCase evaluationCase, RuleRegistry ruleRegistry) {
            DiagnosisReasonDto reason = reason(evaluationCase, ruleRegistry);
            DiagnosisResponse response = new DiagnosisResponse();
            response.setSummary(evaluationCase.note());
            response.setConfidence("HIGH");
            response.setNextActions(new ArrayList<>(evaluationCase.expectedNextActions()));
            response.setReasons(List.of(reason));
            return response;
        }

        private DiagnosisReasonDto reason(EvaluationCase evaluationCase, RuleRegistry ruleRegistry) {
            String primaryRuleId = evaluationCase.expectedRuleIds().get(0);
            DiagnosisRule rule = rulesById(ruleRegistry).get(primaryRuleId);
            assertNotNull(rule, evaluationCase.caseId() + " references unknown rule " + primaryRuleId);

            DiagnosisReasonDto reason = new DiagnosisReasonDto();
            reason.setCode(evaluationCase.expectedReasonCode());
            reason.setTitle(rule.getTitle());
            reason.setLevel(rule.getSeverity());
            reason.setConfidence("HIGH");
            reason.setRuleIds(new ArrayList<>(evaluationCase.expectedRuleIds()));
            reason.setDescription(rule.getDescription());
            reason.setSuggestion(evaluationCase.expectedNextActions().get(0));
            reason.setNextActions(new ArrayList<>(evaluationCase.expectedNextActions()));
            reason.setEvidence(toolDataCatalog.toolEvidence(evaluationCase).entrySet().stream()
                .map(entry -> new DiagnosisEvidenceDto(entry.getKey(), entry.getValue()))
                .toList());
            return reason;
        }

        private Map<String, DiagnosisRule> rulesById(RuleRegistry ruleRegistry) {
            return ruleRegistry.getRules().stream()
                .collect(Collectors.toMap(DiagnosisRule::getRuleId, rule -> rule));
        }

        private DiagnosisResponse fallbackResponse(EvaluationCase evaluationCase) {
            DiagnosisReasonDto reason = new DiagnosisReasonDto();
            reason.setCode(evaluationCase.expectedReasonCode());
            reason.setTitle("需要人工核对");
            reason.setLevel("LOW");
            reason.setConfidence("LOW");
            reason.setRuleIds(List.of("DATA_INCOMPLETE_NEED_RECHECK"));
            reason.setDescription(evaluationCase.note());
            reason.setSuggestion(evaluationCase.expectedNextActions().get(0));
            reason.setNextActions(new ArrayList<>(evaluationCase.expectedNextActions()));
            reason.setEvidence(List.of(new DiagnosisEvidenceDto("fallbackReason", evaluationCase.note())));

            DiagnosisResponse response = new DiagnosisResponse();
            response.setSummary("诊断数据不足，需要人工核对。");
            response.setConfidence("LOW");
            response.setFallback(true);
            response.setFallbackReason(evaluationCase.note());
            response.setNextActions(new ArrayList<>(evaluationCase.expectedNextActions()));
            response.setReasons(List.of(reason));
            return response;
        }
    }

    private record CaseKey(String customerId, String customerCode, String recordDate, String mealType) {

        static CaseKey from(DiagnosisContextDto context) {
            return new CaseKey(
                context.getCustomerId() == null ? null : String.valueOf(context.getCustomerId()),
                context.getCustomerCode(),
                context.getRecordDate(),
                context.getMealType()
            );
        }

        static CaseKey from(Map<String, Object> slots) {
            Object customerId = slots.get("customerId");
            return new CaseKey(
                customerId == null ? null : String.valueOf(customerId),
                slots.get("customerCode") == null ? null : String.valueOf(slots.get("customerCode")),
                slots.get("recordDate") == null ? null : String.valueOf(slots.get("recordDate")),
                slots.get("mealType") == null ? null : String.valueOf(slots.get("mealType"))
            );
        }
    }
}
