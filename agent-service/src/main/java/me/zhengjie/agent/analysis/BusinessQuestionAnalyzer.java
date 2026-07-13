package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;

/** 将自然语言及受控会话上下文分析为业务查询意图。 */
public interface BusinessQuestionAnalyzer {
    /**
     * 分析业务问题，无法可靠识别时返回需要澄清或范围外的受控结果。
     *
     * @param question 用户原始问题
     * @param context 当前会话的受控槽位
     * @return 不包含自由工具、SQL 或敏感数据的分析结果
     */
    BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context);

    /**
     * 分析依赖上一轮业务结果的问法；默认保持旧分析器兼容，不暴露原始查询结果。
     *
     * @param question 用户原始问题
     * @param context 当前受控槽位
     * @param lastBusinessQueryContext 上一轮业务查询的脱敏摘要
     * @return 受控语义分析结果
     */
    default BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context,
                                             LastBusinessQueryContext lastBusinessQueryContext) {
        return analyze(question, context);
    }
}
