package me.zhengjie.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 智能排查聊天请求。
 */
public class AgentChatRequest {

    private String sessionId;

    private String clientMessageId;

    @NotBlank
    private String message;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
