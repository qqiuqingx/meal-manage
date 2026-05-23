package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 后台发起聊天诊断请求。
 */
@Data
public class AgentChatRequest {

    private String sessionId;

    @NotBlank
    private String message;
}
