package me.zhengjie.agent.analysis.domain;

/** 用户与上一轮业务查询的受控交互关系。 */
public enum BusinessInteractionMode {
    NEW_QUERY,
    FOLLOW_UP,
    REFINE,
    CORRECTION,
    RETRY,
    RESET
}
