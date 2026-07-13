package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;

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
}
