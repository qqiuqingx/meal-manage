package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查动作草稿确认响应。
 */
@Data
public class AgentActionConfirmResponse {

    private Long auditId;

    private String actionCode;

    private String status;

    private Boolean success;

    private Boolean idempotentHit;

    private String message;

    private String failureReason;

    private Object executionResult;
}
