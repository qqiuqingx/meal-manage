package me.zhengjie.agent.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisPromptPolicyLoaderTest {

    @Test
    void shouldLoadYamlPolicy() {
        DiagnosisPromptPolicyLoader loader = new DiagnosisPromptPolicyLoader();

        DiagnosisPromptPolicy policy = loader.load();

        assertEquals("MEAL_PLAN_NOT_GENERATED", policy.getScene());
        assertTrue(policy.getVersion() > 0);
        assertTrue(policy.getForbiddenClaims().contains("已修复"));
        assertTrue(policy.getOutputContract().getRequiredFields().contains("summary"));
        assertTrue(policy.getOutputContract().getRequiredFields().contains("reasons"));
        assertTrue(policy.getToolPolicy().getMaxToolCalls() >= 1 && policy.getToolPolicy().getMaxToolCalls() <= 20);
    }

    @Test
    void shouldRejectInvalidScene() {
        DiagnosisPromptPolicyLoader loader = new DiagnosisPromptPolicyLoader(new ByteArrayResource("""
            scene: OTHER_SCENE
            version: 1
            role: test
            outputContract:
              requiredFields: [summary, reasons]
            forbiddenClaims: [已修复]
            toolPolicy:
              maxToolCalls: 8
              requiredBeforeConclusion: [getCustomerProfile]
            evidencePolicy:
              minEvidencePerReason: 1
              requireRuleIds: true
              requireFieldReference: true
            """.getBytes()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, loader::load);

        assertTrue(ex.getMessage().contains("scene"));
    }

    @Test
    void shouldRejectInvalidToolBudget() {
        DiagnosisPromptPolicyLoader loader = new DiagnosisPromptPolicyLoader(new ByteArrayResource("""
            scene: MEAL_PLAN_NOT_GENERATED
            version: 1
            role: test
            outputContract:
              requiredFields: [summary, reasons]
            forbiddenClaims: [已修复]
            toolPolicy:
              maxToolCalls: 21
              requiredBeforeConclusion: [getCustomerProfile]
            evidencePolicy:
              minEvidencePerReason: 1
              requireRuleIds: true
              requireFieldReference: true
            """.getBytes()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, loader::load);

        assertTrue(ex.getMessage().contains("maxToolCalls"));
    }
}
