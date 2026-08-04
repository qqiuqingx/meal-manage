package me.zhengjie.agent.domain.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.application.conversation.ConversationPatch;

/**
 * 智能排查聊天响应。
 */
public class AgentChatResponse {

    private String requestId;
    /** 响应所遵循的跨服务契约版本，当前固定为 v2。 */
    private String contractVersion;
    /** 主系统生成的消息幂等键，Agent 必须原样回传。 */
    private String clientMessageId;
    /** 当前响应基于的主系统会话版本；主系统提交 Patch 时必须使用此版本。 */
    private Long expectedSessionVersion;
    /** 供主系统乐观锁提交的结构化会话增量。 */
    private ConversationPatch conversationPatch;
    private String sessionId;
    private ChatStatus status;
    private String assistantMessage;
    private DiagnosisSlots slots;
    private DiagnosisResponse diagnosisResult;
    private List<String> quickReplies = new ArrayList<>();
    private String conversationStage;

    private List<Map<String, Object>> facts = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean cached;
    private boolean partial;
    private String queriedAt;
    private Map<String, Object> lastBusinessQueryContext;
    /** 由成功工具输出类型确定的结构化业务卡片。 */
    private List<Map<String, Object>> cards = new ArrayList<>();
    /** 经输出护栏校验后的工具事实，供主系统审计和事实引用。 */
    private List<Map<String, Object>> toolFacts = new ArrayList<>();
    /** 工具调用名称、结果条数和失败/缓存状态摘要。 */
    private List<Map<String, Object>> toolTraceSummary = new ArrayList<>();

    public List<Map<String, Object>> getFacts() { return facts; }
    public void setFacts(List<Map<String, Object>> facts) { this.facts = facts == null ? new ArrayList<>() : facts; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached; }
    public boolean isPartial() { return partial; }
    public void setPartial(boolean partial) { this.partial = partial; }
    public String getQueriedAt() { return queriedAt; }
    public void setQueriedAt(String queriedAt) { this.queriedAt = queriedAt; }
    public Map<String, Object> getLastBusinessQueryContext() { return lastBusinessQueryContext; }
    public void setLastBusinessQueryContext(Map<String, Object> lastBusinessQueryContext) { this.lastBusinessQueryContext = lastBusinessQueryContext; }
    public List<Map<String, Object>> getCards() { return cards; }
    public void setCards(List<Map<String, Object>> value) { cards = value == null ? new ArrayList<>() : value; }
    public List<Map<String, Object>> getToolFacts() { return toolFacts; }
    public void setToolFacts(List<Map<String, Object>> value) { toolFacts = value == null ? new ArrayList<>() : value; }
    public List<Map<String, Object>> getToolTraceSummary() { return toolTraceSummary; }
    public void setToolTraceSummary(List<Map<String, Object>> value) { toolTraceSummary = value == null ? new ArrayList<>() : value; }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getContractVersion() { return contractVersion; }
    public void setContractVersion(String contractVersion) { this.contractVersion = contractVersion; }
    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public Long getExpectedSessionVersion() { return expectedSessionVersion; }
    public void setExpectedSessionVersion(Long expectedSessionVersion) { this.expectedSessionVersion = expectedSessionVersion; }
    public ConversationPatch getConversationPatch() { return conversationPatch; }
    public void setConversationPatch(ConversationPatch conversationPatch) { this.conversationPatch = conversationPatch; }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public ChatStatus getStatus() {
        return status;
    }

    public void setStatus(ChatStatus status) {
        this.status = status;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(String assistantMessage) {
        this.assistantMessage = assistantMessage;
    }

    public DiagnosisSlots getSlots() {
        return slots;
    }

    public void setSlots(DiagnosisSlots slots) {
        this.slots = slots;
    }

    public DiagnosisResponse getDiagnosisResult() {
        return diagnosisResult;
    }

    public void setDiagnosisResult(DiagnosisResponse diagnosisResult) {
        this.diagnosisResult = diagnosisResult;
    }

    public List<String> getQuickReplies() {
        return quickReplies;
    }

    public void setQuickReplies(List<String> quickReplies) {
        this.quickReplies = quickReplies;
    }

    public String getConversationStage() {
        return conversationStage;
    }

    public void setConversationStage(String conversationStage) {
        this.conversationStage = conversationStage;
    }
}
