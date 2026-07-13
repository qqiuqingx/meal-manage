package me.zhengjie.agent.query;

import java.util.List;

/**
 * QueryPlan 校验结果，missingFields 非空时表示需要客服补充条件。
 */
public record AgentQueryPlanValidationResult(List<AgentQueryPlanValidationError> errors,
                                             List<String> missingFields) {
    public boolean isValid() { return errors == null || errors.isEmpty(); }
    public boolean requiresFollowUp() { return missingFields != null && !missingFields.isEmpty(); }
}
