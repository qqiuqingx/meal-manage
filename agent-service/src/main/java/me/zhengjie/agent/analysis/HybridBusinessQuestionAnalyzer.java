package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;

/**
 * 模型优先、规则兜底的业务语义分析器。模型只输出受控语义，工具和查询范围仍由服务端规划。
 */
public class HybridBusinessQuestionAnalyzer implements BusinessQuestionAnalyzer {
    private static final double DEFAULT_MODEL_CONFIDENCE_THRESHOLD = 0.80D;
    private final BusinessQuestionAnalyzer ruleAnalyzer;
    private final BusinessQuestionAnalyzer llmAnalyzer;
    private final double modelConfidenceThreshold;

    public HybridBusinessQuestionAnalyzer(BusinessQuestionAnalyzer ruleAnalyzer, BusinessQuestionAnalyzer llmAnalyzer) {
        this(ruleAnalyzer, llmAnalyzer, DEFAULT_MODEL_CONFIDENCE_THRESHOLD);
    }

    /**
     * 创建可配置置信度阈值的混合分析器。
     *
     * @param ruleAnalyzer 模型不可用或结果不安全时使用的确定性兜底
     * @param llmAnalyzer 负责业务对象、省略、指代和纠错理解的模型分析器
     * @param modelConfidenceThreshold 可执行模型语义的最低置信度
     */
    public HybridBusinessQuestionAnalyzer(BusinessQuestionAnalyzer ruleAnalyzer, BusinessQuestionAnalyzer llmAnalyzer,
                                          double modelConfidenceThreshold) {
        this.ruleAnalyzer = ruleAnalyzer;
        this.llmAnalyzer = llmAnalyzer;
        this.modelConfidenceThreshold = modelConfidenceThreshold;
    }

    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context) {
        return analyze(question, context, null);
    }

    /** {@inheritDoc} */
    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context,
                                            LastBusinessQueryContext lastBusinessQueryContext) {
        BusinessQuestionAnalysis llm = llmAnalyzer == null ? null : llmAnalyzer.analyze(question, context, lastBusinessQueryContext);
        if (llm != null && llm.getConfidence() >= modelConfidenceThreshold) return llm;
        return ruleAnalyzer.analyze(question, context, lastBusinessQueryContext);
    }
}
