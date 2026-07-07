package me.zhengjie.agent.rule;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleRegistryLoaderTest {

    @Test
    void shouldLoadMealPlanRulesWithUniqueIdsAndRequiredData() {
        FileSystemRuleRegistryLoader loader = new FileSystemRuleRegistryLoader(Path.of("rules"));

        RuleRegistry registry = loader.load("MEAL_PLAN_NOT_GENERATED");

        assertEquals("MEAL_PLAN_NOT_GENERATED", registry.getScene());
        assertNotNull(registry.getVersionDigest());
        assertFalse(registry.getRules().isEmpty());

        Set<String> ruleIds = registry.getRules().stream()
            .map(DiagnosisRule::getRuleId)
            .collect(Collectors.toSet());

        assertEquals(registry.getRules().size(), ruleIds.size());
        assertTrue(ruleIds.contains("CUSTOMER_NOT_FOUND"));
        assertTrue(ruleIds.contains("ORDER_MISSING"));
        assertTrue(ruleIds.contains("MEAL_PLAN_GENERATED_FAILED"));
        assertTrue(registry.getRules().stream().allMatch(rule -> rule.getVersion() != null && rule.getVersion() > 0));
        assertTrue(registry.getRules().stream().allMatch(rule -> rule.getReasonCode() != null && !rule.getReasonCode().isBlank()));
        assertTrue(registry.getRules().stream().allMatch(rule -> rule.getTriggerConditions() != null && !rule.getTriggerConditions().isEmpty()));
        assertTrue(registry.getRules().stream().allMatch(rule -> rule.getRequiredTools() != null && !rule.getRequiredTools().isEmpty()));
        assertTrue(registry.getRules().stream().allMatch(rule -> rule.getRequiredData() != null && !rule.getRequiredData().isEmpty()));
        assertTrue(registry.getRules().stream().allMatch(rule -> rule.getEvidenceFields() != null && !rule.getEvidenceFields().isEmpty()));
        assertTrue(registry.getRules().stream().allMatch(rule -> rule.getNextActions() != null && !rule.getNextActions().isEmpty()));
        assertTrue(registry.getRules().stream().allMatch(rule -> rule.getOwner() != null && !rule.getOwner().isBlank()));
        assertTrue(registry.getRules().stream()
            .flatMap(rule -> rule.getRequiredTools().stream())
            .allMatch(Set.of(
                "getCustomerProfile",
                "listCustomerOrders",
                "getMealPlan",
                "getCandidateDishStats",
                "getCustomerExcludeDates",
                "getOrderMealBalance",
                "getPackageSpec",
                "getDishCandidateDetail",
                "listVerificationLogs",
                "listMealRefunds",
                "getMealPlanGenerationSnapshot"
            )::contains));
    }

    @Test
    void shouldLoadRulesFromClasspathWhenFileSystemPathDoesNotExist() {
        FileSystemRuleRegistryLoader loader = new FileSystemRuleRegistryLoader(Path.of("missing-rules"));

        RuleRegistry registry = loader.load("MEAL_PLAN_NOT_GENERATED");

        assertFalse(registry.getRules().isEmpty());
        assertTrue(registry.getRules().stream()
            .map(DiagnosisRule::getRuleId)
            .anyMatch("ORDER_MISSING"::equals));
    }
}
