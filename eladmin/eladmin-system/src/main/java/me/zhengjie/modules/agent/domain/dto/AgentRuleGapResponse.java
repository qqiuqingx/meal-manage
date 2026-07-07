package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查规则缺口写入响应。
 */
@Data
public class AgentRuleGapResponse {

    private Long id;

    private String status;

    private String message;
}
