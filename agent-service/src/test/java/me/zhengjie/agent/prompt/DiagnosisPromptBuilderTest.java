package me.zhengjie.agent.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.rule.DiagnosisRule;
import me.zhengjie.agent.rule.RuleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisPromptBuilderTest {

    @Test
    void shouldBuildPromptWithContextRulesAndJsonContract() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setCustomerId(1001L);
        context.setCustomerName("张三");
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");

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

        context.setCustomerProfile(Map.of("excludeDates", List.of(Map.of("date", "2026-05-17", "mealTypes", List.of("LUNCH")))));
        context.setOrders(List.of(Map.<String, Object>of("orderId", 88L, "startDate", "2026-05-01", "remainingCount", 0)));
        context.setMealPlan(Map.of("planId", 99L, "status", "FAILED"));
        context.setCustomerPlans(List.of(Map.of("status", "FAILED", "failReason", "候选菜为空")));
        context.setCandidateDishStats(List.of(Map.<String, Object>of("packageId", 1L, "afterFilterCount", 0)));

        DiagnosisPromptBuilder builder = new DiagnosisPromptBuilder(new ObjectMapper());

        String prompt = builder.build(context, registry);

        assertTrue(prompt.contains("1001"));
        assertTrue(prompt.contains("2026-05-17"));
        assertTrue(prompt.contains("LUNCH"));
        assertTrue(prompt.contains("CUSTOMER_EXCLUDE_DATE_HIT"));
        assertTrue(prompt.contains("excludeDates"));
        assertTrue(prompt.contains("remainingCount"));
        assertTrue(prompt.contains("afterFilterCount"));
        assertTrue(prompt.contains("failReason"));
        assertTrue(prompt.contains("JSON"));
        assertTrue(prompt.contains("人工确认"));
    }
}
