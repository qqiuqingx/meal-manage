package me.zhengjie.agent.evaluation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealPlanDiagnosisEvaluationCasesTest {

    @Test
    void shouldProvideAtLeastOneHundredStructuredEvaluationCases() throws IOException {
        String yaml = readCases();

        assertEquals(101, countMatches(yaml, "^  - caseId:", Pattern.MULTILINE));
        assertEquals(101, countMatches(yaml, "expectedReasonCode:", 0));
        assertEquals(101, countMatches(yaml, "expectedEvidenceFields:", 0));
        assertEquals(101, countMatches(yaml, "expectedNextActions:", 0));
        assertTrue(yaml.contains("CUSTOMER_EXCLUDE_DATE_HIT"));
        assertTrue(yaml.contains("ORDER_NOT_EFFECTIVE"));
        assertTrue(yaml.contains("ORDER_REMAINING_COUNT_NOT_ENOUGH"));
        assertTrue(yaml.contains("CANDIDATE_DISH_EMPTY"));
        assertTrue(yaml.contains("MEAL_PLAN_GENERATION_FAILED"));
        assertTrue(yaml.contains("ORDER_EXPIRED"));
        assertTrue(yaml.contains("ORDER_MEAL_TYPE_MISMATCH"));
        assertTrue(yaml.contains("PACKAGE_SPEC_MISSING"));
        assertTrue(yaml.contains("REFUND_OR_STOP_MEAL_HIT"));
        assertTrue(yaml.contains("VERIFICATION_CONSUMED_COUNT"));
        assertTrue(yaml.contains("MEAL_PLAN_ALREADY_EXISTS_BUT_CUSTOMER_MISSING"));
        assertTrue(yaml.contains("动作草稿"));
        assertTrue(yaml.contains("反馈闭环"));
        assertTrue(yaml.contains("运营"));
        assertTrue(yaml.contains("规则维护"));
        assertTrue(yaml.contains("allowFallback: true"));
        assertTrue(yaml.contains("allowFallback: false"));
    }

    private String readCases() throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("evaluation/meal-plan-diagnosis-cases.yaml")) {
            assertNotNull(inputStream);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int countMatches(String value, String regex, int flags) {
        Matcher matcher = Pattern.compile(regex, flags).matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
