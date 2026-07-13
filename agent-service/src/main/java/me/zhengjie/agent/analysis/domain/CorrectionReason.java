package me.zhengjie.agent.analysis.domain;

/** 用户否定上一轮业务查询时的受控原因分类。 */
public enum CorrectionReason {
    WRONG_TARGET,
    WRONG_FILTER,
    PREVIOUS_RESULT_IMPLAUSIBLE,
    MISSING_DATA,
    UNKNOWN
}
