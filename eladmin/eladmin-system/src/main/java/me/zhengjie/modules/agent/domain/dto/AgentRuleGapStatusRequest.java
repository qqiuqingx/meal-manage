package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 智能排查规则缺口状态更新请求。
 */
@Data
public class AgentRuleGapStatusRequest {

    @NotBlank(message = "status不能为空")
    private String status;

    private String owner;

    private String comment;
}
