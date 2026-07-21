package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.MealScope;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证业务语义由模型优先决定，规则仅在模型结果不可用时兜底。 */
class HybridBusinessQuestionAnalyzerTest {

    @Test
    void shouldUseLlmSemanticAnalysisBeforeHighConfidenceRuleCandidate() {
        AtomicBoolean llmCalled = new AtomicBoolean();
        BusinessQuestionAnalyzer rule = (question, context) -> analysis("RULE", 0.99D, null, null);
        BusinessQuestionAnalyzer llm = new BusinessQuestionAnalyzer() {
            @Override
            public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context) {
                return analyze(question, context, null);
            }

            @Override
            public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context,
                                                     LastBusinessQueryContext lastBusinessQueryContext) {
                llmCalled.set(true);
                return analysis("LLM", 0.94D, BusinessQueryTarget.MEAL_PLAN_ALLERGY_ANALYSIS, MealScope.ALL_AVAILABLE);
            }
        };
        HybridBusinessQuestionAnalyzer analyzer = new HybridBusinessQuestionAnalyzer(rule, llm, 0.80D);

        BusinessQuestionAnalysis result = analyzer.analyze("今天排餐的客户 对哪些菜过敏", new DiagnosisSlots());

        assertTrue(llmCalled.get());
        assertEquals("LLM", result.getSource());
        assertEquals(BusinessQueryTarget.MEAL_PLAN_ALLERGY_ANALYSIS, result.getQueryTarget());
        assertEquals(MealScope.ALL_AVAILABLE, result.getMealScope());
    }

    @Test
    void shouldMarkRuleFallbackSeparatelyFromClarification() {
        BusinessQuestionAnalyzer rule = (question, context) -> analysis("RULE", 0.96D, null, null);
        HybridBusinessQuestionAnalyzer analyzer = new HybridBusinessQuestionAnalyzer(rule, null, 0.80D);

        BusinessQuestionAnalysis result = analyzer.analyze("今天待排餐客户", new DiagnosisSlots());

        assertEquals("RULE_FALLBACK", result.getSource());
        assertEquals("MODEL_UNAVAILABLE", result.getFallbackReason());
    }

    /** 无领域、零置信度的通用规则澄清不能覆盖高置信模型识别出的上下文追问。 */
    @Test
    void shouldKeepHighConfidenceModelWhenRuleOnlyHasGenericClarification() {
        BusinessQuestionAnalysis model = analysis("LLM", 0.90D, BusinessQueryTarget.CUSTOMER_MEAL_PLAN, MealScope.ALL_AVAILABLE);
        BusinessQuestionAnalyzer llm = (question, context) -> model;
        BusinessQuestionAnalyzer genericRule = (question, context) -> {
            BusinessQuestionAnalysis unknown = new BusinessQuestionAnalysis();
            unknown.setConfidence(0D); unknown.setRequiresClarification(true);
            unknown.setClarificationQuestion("请说明要查询哪类数据");
            return unknown;
        };

        BusinessQuestionAnalysis result = new HybridBusinessQuestionAnalyzer(genericRule, llm, 0.80D)
            .analyze("他以前安排过膳食吗", new DiagnosisSlots());

        assertEquals("LLM", result.getSource());
        assertEquals(BusinessQueryTarget.CUSTOMER_MEAL_PLAN, result.getQueryTarget());
    }

    @Test
    void shouldPreferHighPrecisionCustomerTotalRuleOverConflictingModelGuess() {
        BusinessQuestionAnalysis model = analysis("LLM", 0.95D, null, null);
        model.setMetrics(List.of(AgentQueryMetric.ACTIVE_CUSTOMER_COUNT));
        model.setDimensions(List.of(AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE));
        model.getFilters().setPage(0); model.getFilters().setSize(0); model.getFilters().setRecentLimit(0);
        BusinessQuestionAnalyzer llm = (question, context) -> model;
        HybridBusinessQuestionAnalyzer analyzer = new HybridBusinessQuestionAnalyzer(
            new RuleBasedBusinessQuestionAnalyzer(), llm, 0.80D);

        BusinessQuestionAnalysis result = analyzer.analyze("现在系统中还有多少客户", new DiagnosisSlots());

        assertEquals(List.of(AgentQueryMetric.CUSTOMER_PROFILE_COUNT), result.getMetrics());
        assertEquals("RULE_FALLBACK", result.getSource());
        assertEquals("MODEL_RULE_GUARDRAIL_CONFLICT", result.getFallbackReason());
    }

    @Test
    void shouldRemoveModelDefaultZerosAndUnrequestedDimensions() {
        BusinessQuestionAnalysis model = analysis("LLM", 0.95D, null, null);
        model.setMetrics(List.of(AgentQueryMetric.ACTIVE_CUSTOMER_COUNT));
        model.setDimensions(List.of(AgentQueryDimension.PACKAGE));
        model.getFilters().setPage(0); model.getFilters().setSize(0); model.getFilters().setRecentLimit(0);
        BusinessQuestionAnalyzer llm = (question, context) -> model;
        HybridBusinessQuestionAnalyzer analyzer = new HybridBusinessQuestionAnalyzer(
            new RuleBasedBusinessQuestionAnalyzer(), llm, 0.80D);

        BusinessQuestionAnalysis result = analyzer.analyze("活跃客户有多少", new DiagnosisSlots());

        assertTrue(result.getDimensions().isEmpty());
        assertEquals(null, result.getFilters().getPage());
        assertEquals(null, result.getFilters().getSize());
        assertEquals(null, result.getFilters().getRecentLimit());
    }

    private BusinessQuestionAnalysis analysis(String source, double confidence, BusinessQueryTarget target, MealScope mealScope) {
        BusinessQuestionAnalysis result = new BusinessQuestionAnalysis();
        result.setSource(source);
        result.setConfidence(confidence);
        result.setDomains(List.of(target == null ? AgentQueryDomain.OPERATION_STATISTICS : AgentQueryDomain.MEAL_PLAN));
        result.setQueryTarget(target);
        result.setMealScope(mealScope);
        return result;
    }
}
