package me.zhengjie.agent.query;

/**
 * QueryPlan 校验失败项，供编排层决定拒绝或向客服追问。
 */
public record AgentQueryPlanValidationError(String field, String code, String message) {
}
