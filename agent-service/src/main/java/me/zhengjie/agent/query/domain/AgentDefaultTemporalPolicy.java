package me.zhengjie.agent.query.domain;

/** 指标在用户未明确时间时采用的服务端时间策略。 */
public enum AgentDefaultTemporalPolicy {
    NONE,
    CURRENT_DAY,
    REQUIRE_EXPLICIT,
    INHERIT_OR_CURRENT
}
