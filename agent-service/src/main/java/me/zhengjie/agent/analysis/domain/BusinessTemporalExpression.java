package me.zhengjie.agent.analysis.domain;

/**
 * 模型可选择的受控业务时间表达。相对时间只表达语义，不携带模型计算后的具体日期。
 */
public enum BusinessTemporalExpression {
    UNSPECIFIED,
    CURRENT_DAY,
    PREVIOUS_DAY,
    NEXT_DAY,
    CURRENT_WEEK,
    EXPLICIT_DATE,
    EXPLICIT_RANGE,
    INHERIT_PREVIOUS
}
