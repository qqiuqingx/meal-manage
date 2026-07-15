package me.zhengjie.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;

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
    /** 主系统持久化的待补条件查询，只允许由可信内部会话服务下发。 */
    private PendingBusinessQueryContext pendingBusinessQueryContext;
    /** 主系统持久化的最近已执行查询脱敏摘要。 */
    private LastBusinessQueryContext lastBusinessQueryContext;

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
    public PendingBusinessQueryContext getPendingBusinessQueryContext() { return pendingBusinessQueryContext; }
    public void setPendingBusinessQueryContext(PendingBusinessQueryContext pendingBusinessQueryContext) { this.pendingBusinessQueryContext = pendingBusinessQueryContext; }
    public LastBusinessQueryContext getLastBusinessQueryContext() { return lastBusinessQueryContext; }
    public void setLastBusinessQueryContext(LastBusinessQueryContext lastBusinessQueryContext) { this.lastBusinessQueryContext = lastBusinessQueryContext; }
}
