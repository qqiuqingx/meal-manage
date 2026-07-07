package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查动作确认审计查询条件。
 */
@Data
public class AgentActionAuditQueryCriteria {

    private String requestId;

    private String sessionId;

    private String actionCode;

    private String status;

    private String operator;

    private Integer page = 0;

    private Integer size = 10;
}
