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
        BusinessQuestionAnalysis fallback = ruleAnalyzer.analyze(question, context, lastBusinessQueryContext);
        if (llm != null && llm.getConfidence() >= modelConfidenceThreshold) {
            normalizeOptionalLimits(llm);
            if (shouldUseRuleGuardrail(llm, fallback)) {
                fallback.setSource("RULE_FALLBACK");
                fallback.setFallbackReason("MODEL_CONFLICTS_WITH_RULE_GUARDRAIL");
                return fallback;
            }
            alignImplicitDimensions(llm, fallback);
            return llm;
        }
        if (fallback != null) {
            fallback.setSource("RULE_FALLBACK");
            String failureReason = llm == null && llmAnalyzer instanceof LlmBusinessQuestionAnalyzer
                ? ((LlmBusinessQuestionAnalyzer) llmAnalyzer).getLastFailureReason() : null;
            fallback.setFallbackReason(llm == null
                ? (failureReason == null ? "MODEL_UNAVAILABLE" : failureReason) : "MODEL_LOW_CONFIDENCE");
        }
        return fallback;
    }

    /** 将模型用 0 表示的未指定分页参数还原为空，负数仍交由 QueryPlan 校验器拒绝。 */
    private void normalizeOptionalLimits(BusinessQuestionAnalysis analysis) {
        if (analysis == null || analysis.getFilters() == null) return;
        if (Integer.valueOf(0).equals(analysis.getFilters().getPage())) analysis.getFilters().setPage(null);
        if (Integer.valueOf(0).equals(analysis.getFilters().getSize())) analysis.getFilters().setSize(null);
        if (Integer.valueOf(0).equals(analysis.getFilters().getRecentLimit())) analysis.getFilters().setRecentLimit(null);
    }

    /** 高精度规则识别出的关键歧义或明确指标优先于冲突的模型猜测。 */
    private boolean shouldUseRuleGuardrail(BusinessQuestionAnalysis llm, BusinessQuestionAnalysis rule) {
        if (rule == null) return false;
        if (rule.isRequiresClarification()) return true;
        if (rule.getConfidence() >= 0.90D && rule.getQueryTarget() == me.zhengjie.agent.analysis.domain.BusinessQueryTarget.CUSTOMER
            && llm.getQueryTarget() != me.zhengjie.agent.analysis.domain.BusinessQueryTarget.CUSTOMER) return true;
        return rule.getConfidence() >= 0.90D && rule.getMetrics() != null && !rule.getMetrics().isEmpty()
            && !rule.getMetrics().equals(llm.getMetrics());
    }

    /** 同一高置信指标未出现规则可识别分组时，禁止模型凭空增加套餐或来源维度。 */
    private void alignImplicitDimensions(BusinessQuestionAnalysis llm, BusinessQuestionAnalysis rule) {
        if (rule == null || rule.isRequiresClarification() || rule.getConfidence() < 0.90D
            || rule.getMetrics() == null || !rule.getMetrics().equals(llm.getMetrics())) return;
        llm.setDimensions(rule.getDimensions() == null ? java.util.List.of() : rule.getDimensions());
    }
}
