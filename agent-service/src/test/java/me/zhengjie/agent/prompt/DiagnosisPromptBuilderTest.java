package me.zhengjie.agent.prompt;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.rule.DiagnosisRule;
import me.zhengjie.agent.rule.RuleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisPromptBuilderTest {

    @Test
    void shouldBuildPromptWithRequestRulesAndToolInstructionsOnly() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setCustomerId(1001L);
        context.setCustomerCode("C1001");
        context.setCustomerName("张三");
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");
        context.setCustomerProfile(Map.of("excludeDates", List.of(Map.of("date", "2026-05-17", "mealTypes", List.of("LUNCH")))));
        context.setOrders(List.of(Map.<String, Object>of("orderId", 88L, "remainingCount", 0)));
        context.setMealPlan(Map.of("planId", 99L, "status", "PLAN_FAILED"));
        context.setCustomerPlans(List.of(Map.of("status", "CUSTOMER_PLAN_FAILED", "failReason", "候选菜为空")));
        context.setCandidateDishStats(List.of(Map.<String, Object>of("packageId", 1L, "afterFilterCount", 0)));

        DiagnosisRule rule = new DiagnosisRule();
        rule.setRuleId("CUSTOMER_EXCLUDE_DATE_HIT");
        rule.setVersion(1);
        rule.setTitle("命中客户排除日期");
        rule.setDescription("客户档案配置了目标日期和餐次不配送。");
        rule.setRequiredData(List.of("customerProfile.excludeDates"));

        RuleRegistry registry = new RuleRegistry();
        registry.setScene("MEAL_PLAN_NOT_GENERATED");
        registry.setVersionDigest("digest-1");
        registry.setRules(List.of(rule));

        DiagnosisPromptBuilder builder = new DiagnosisPromptBuilder();

        String prompt = builder.build(context, registry);

        assertTrue(prompt.contains("1001"));
        assertTrue(prompt.contains("C1001"));
        assertTrue(prompt.contains("2026-05-17"));
        assertTrue(prompt.contains("LUNCH"));
        assertTrue(prompt.contains("CUSTOMER_EXCLUDE_DATE_HIT"));
        assertTrue(prompt.contains("getCustomerProfile"));
        assertTrue(prompt.contains("listCustomerOrders"));
        assertTrue(prompt.contains("getMealPlan"));
        assertTrue(prompt.contains("getCandidateDishStats"));
        assertFalse(prompt.contains("张三"));
        assertFalse(prompt.contains("客户名称"));
        assertFalse(prompt.contains("excludeDates"));
        assertFalse(prompt.contains("remainingCount"));
        assertFalse(prompt.contains("PLAN_FAILED"));
        assertFalse(prompt.contains("CUSTOMER_PLAN_FAILED"));
        assertFalse(prompt.contains("failReason"));
        assertFalse(prompt.contains("afterFilterCount"));
        assertFalse(prompt.contains("业务上下文 JSON"));
    }

    @Test
    void shouldBuildPromptWhenRulesAreNull() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setCustomerId(1001L);
        context.setCustomerCode("C1001");
        context.setCustomerName("张三");
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");

        RuleRegistry registry = new RuleRegistry();
        registry.setScene("MEAL_PLAN_NOT_GENERATED");
        registry.setVersionDigest("digest-1");
        registry.setRules(null);

        DiagnosisPromptBuilder builder = new DiagnosisPromptBuilder();

        String prompt = builder.build(context, registry);

        assertTrue(prompt.contains("1001"));
        assertTrue(prompt.contains("C1001"));
        assertTrue(prompt.contains("2026-05-17"));
        assertTrue(prompt.contains("LUNCH"));
        assertTrue(prompt.contains("规则列表"));
        assertFalse(prompt.contains("张三"));
        assertFalse(prompt.contains("客户名称"));
    }
}
