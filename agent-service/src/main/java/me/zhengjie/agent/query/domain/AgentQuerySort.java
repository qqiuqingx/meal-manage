package me.zhengjie.agent.query.domain;

/**
 * 运营统计允许的固定排序方式，禁止自由排序字段和 SQL 表达式。
 */
public enum AgentQuerySort {
    COUNT_DESC,
    COUNT_ASC,
    DIMENSION_ASC,
    DIMENSION_DESC
}
