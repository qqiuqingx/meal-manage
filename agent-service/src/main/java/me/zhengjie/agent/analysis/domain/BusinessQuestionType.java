package me.zhengjie.agent.analysis.domain;

/** 客服问题的顶层类型，业务能力不再通过不断扩展 ChatIntent 表示。 */
public enum BusinessQuestionType {
    BUSINESS_QUERY,
    OUT_OF_SCOPE
}
