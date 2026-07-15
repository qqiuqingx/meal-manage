package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 灰度旁路分析器：规则结果继续生效，同时执行 LLM 并只记录领域、指标和时间枚举差异。
 */
public class ShadowBusinessQuestionAnalyzer implements BusinessQuestionAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(ShadowBusinessQuestionAnalyzer.class);
    private final BusinessQuestionAnalyzer ruleAnalyzer;
    private final BusinessQuestionAnalyzer llmAnalyzer;

    public ShadowBusinessQuestionAnalyzer(BusinessQuestionAnalyzer ruleAnalyzer, BusinessQuestionAnalyzer llmAnalyzer) {
        this.ruleAnalyzer = ruleAnalyzer;
        this.llmAnalyzer = llmAnalyzer;
    }

    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context) {
        return analyze(question, context, null);
    }

    /** {@inheritDoc} */
    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context, LastBusinessQueryContext lastContext) {
        BusinessQuestionAnalysis rule = ruleAnalyzer.analyze(question, context, lastContext);
        BusinessQuestionAnalysis llm = llmAnalyzer == null ? null : llmAnalyzer.analyze(question, context, lastContext);
        log.info("业务语义旁路对比 ruleDomains={} ruleMetrics={} ruleTemporal={} llmDomains={} llmMetrics={} llmTemporal={} comparable={}",
            rule == null ? null : rule.getDomains(), rule == null ? null : rule.getMetrics(), temporal(rule),
            llm == null ? null : llm.getDomains(), llm == null ? null : llm.getMetrics(), temporal(llm), llm != null);
        if (rule != null) rule.setSource("RULE");
        return rule;
    }

    private Object temporal(BusinessQuestionAnalysis analysis) {
        return analysis == null || analysis.getTemporal() == null ? null : analysis.getTemporal().getExpression();
    }
}
