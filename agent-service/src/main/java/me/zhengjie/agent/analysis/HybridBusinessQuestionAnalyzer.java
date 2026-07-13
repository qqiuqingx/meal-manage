package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;

/**
 * 规则优先、模型补充的分析器。模型异常、低置信度或返回不安全结构时不触发自由查询。
 */
public class HybridBusinessQuestionAnalyzer implements BusinessQuestionAnalyzer {
    private static final double MODEL_CONFIDENCE_THRESHOLD = 0.80D;
    private final BusinessQuestionAnalyzer ruleAnalyzer;
    private final BusinessQuestionAnalyzer llmAnalyzer;

    public HybridBusinessQuestionAnalyzer(BusinessQuestionAnalyzer ruleAnalyzer, BusinessQuestionAnalyzer llmAnalyzer) {
        this.ruleAnalyzer = ruleAnalyzer;
        this.llmAnalyzer = llmAnalyzer;
    }

    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context) {
        BusinessQuestionAnalysis rule = ruleAnalyzer.analyze(question, context);
        if (rule != null && rule.getConfidence() >= MODEL_CONFIDENCE_THRESHOLD && !rule.isRequiresClarification()) return rule;
        BusinessQuestionAnalysis llm = llmAnalyzer == null ? null : llmAnalyzer.analyze(question, context);
        if (llm != null && llm.getConfidence() >= MODEL_CONFIDENCE_THRESHOLD) return llm;
        return rule;
    }
}
