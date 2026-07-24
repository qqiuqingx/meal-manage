package me.zhengjie.agent.rule;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
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
        assertTrue(registry.getRules().stream().map(DiagnosisRule::getRuleId).anyMatch("PACKAGE_SPEC_MISSING"::equals));
        assertTrue(registry.getRules().stream().map(DiagnosisRule::getRuleId).anyMatch("REFUND_OR_STOP_MEAL_HIT"::equals));
        assertTrue(registry.getRules().stream().map(DiagnosisRule::getRuleId).anyMatch("VERIFICATION_CONSUMED_COUNT"::equals));
    }

    @Test
    void shouldKeepFilesystemAndClasspathRuleSetsAndDigestsConsistent() {
        RuleRegistry fileSystem = new FileSystemRuleRegistryLoader(Path.of("rules")).load("MEAL_PLAN_NOT_GENERATED");
        RuleRegistry classpath = new FileSystemRuleRegistryLoader(Path.of("missing-rules")).load("MEAL_PLAN_NOT_GENERATED");

        assertEquals(fileSystem.getVersionDigest(), classpath.getVersionDigest());
        assertEquals(fileSystem.getRules().stream().map(DiagnosisRule::getRuleId).collect(Collectors.toSet()),
            classpath.getRules().stream().map(DiagnosisRule::getRuleId).collect(Collectors.toSet()));
    }

    @Test
    void shouldLoadNewExternalSceneWithoutChangingLoaderCode() throws IOException {
        Path root = Files.createTempDirectory("agent-rules-");
        Path scene = Files.createDirectories(root.resolve("inventory-check"));
        Files.writeString(scene.resolve("rule.yaml"), """
            - ruleId: INVENTORY_EMPTY
              reasonCode: INVENTORY_EMPTY
              version: 1
              title: 库存为空
              requiredTools:
                - getMealPlan
              evidenceFields:
                - inventory.availableCount
              nextActions:
                - 补充库存
              owner: inventory
            """);

        RuleRegistry registry = new FileSystemRuleRegistryLoader(root).load("INVENTORY_CHECK");

        assertEquals("INVENTORY_CHECK", registry.getScene());
        assertEquals("INVENTORY_EMPTY", registry.getRules().get(0).getRuleId());
    }
}
