package me.zhengjie.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 智能排查聊天请求。
 */
public class AgentChatRequest {

    private String sessionId;

    private String clientMessageId;

    @NotBlank
    private String message;

    /** 主系统持久化的最近业务槽位，用于跨实例恢复会话上下文。 */
    private DiagnosisSlots contextSlots;

    /** 主系统依据当前客服权限下发的本轮固定工具白名单。 */
    private List<String> availableTools;

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

    public DiagnosisSlots getContextSlots() { return contextSlots; }
    public void setContextSlots(DiagnosisSlots contextSlots) { this.contextSlots = contextSlots; }
    public List<String> getAvailableTools() { return availableTools; }
    public void setAvailableTools(List<String> availableTools) { this.availableTools = availableTools; }
}
