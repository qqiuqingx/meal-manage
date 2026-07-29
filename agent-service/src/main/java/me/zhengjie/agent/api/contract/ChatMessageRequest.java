package me.zhengjie.agent.api.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 客户端可提交的聊天消息；不包含工具、权限或会话上下文等可信字段。
 */
public class ChatMessageRequest {

    @NotBlank
    @Size(max = 128)
    private String sessionId;
    @NotBlank
    @Size(max = 128)
    private String clientMessageId;
    @NotBlank
    @Size(max = 4000)
    private String message;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
