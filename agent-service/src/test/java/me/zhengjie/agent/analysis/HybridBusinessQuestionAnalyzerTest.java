package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.MealScope;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
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
