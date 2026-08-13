package me.zhengjie.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

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
    /** 主系统快照版本，供 v2 信封回传 optimistic-lock 预期值。 */
    private Long sessionVersion;
    /** 主系统持久化的最近已执行查询脱敏摘要。 */
    private Map<String, Object> lastBusinessQueryContext;
    /** 主系统按当前客服和会话校验后提供的活动表单草稿上下文。 */
    private Map<String, Object> formDraftContext;

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
    public Long getSessionVersion() { return sessionVersion; }
    public void setSessionVersion(Long sessionVersion) { this.sessionVersion = sessionVersion; }
    public Map<String, Object> getLastBusinessQueryContext() { return lastBusinessQueryContext; }
    public void setLastBusinessQueryContext(Map<String, Object> lastBusinessQueryContext) { this.lastBusinessQueryContext = lastBusinessQueryContext; }
    public Map<String, Object> getFormDraftContext() { return formDraftContext; }
    public void setFormDraftContext(Map<String, Object> formDraftContext) { this.formDraftContext = formDraftContext; }
}
