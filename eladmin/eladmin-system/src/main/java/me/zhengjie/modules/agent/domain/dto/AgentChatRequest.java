package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 后台发起聊天诊断请求。
 */
@Data
public class AgentChatRequest {

    private String sessionId;

    private String clientMessageId;

    @NotBlank
    private String message;

    /** 主系统持久化的最近业务槽位，用于 agent-service 重启或多实例切换后的上下文恢复。 */
    private DiagnosisSlots contextSlots;

    /** 主系统按当前客服权限计算的本轮可用工具名，不传递完整权限集合。 */
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
