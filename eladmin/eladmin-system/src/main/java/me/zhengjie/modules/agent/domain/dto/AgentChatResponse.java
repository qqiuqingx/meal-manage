package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台展示的聊天诊断结果。
 */
@Data
public class AgentChatResponse {

    private String requestId;

    private String sessionId;

    private String clientMessageId;

    private String status;

    private String assistantMessage;

    private DiagnosisSlots slots;

    private Map<String, String> slotConfidence = new LinkedHashMap<>();

    private List<String> missingSlots = new ArrayList<>();

    private AgentDiagnosisResponse diagnosisResult;

    private List<String> quickReplies = new ArrayList<>();

    private String conversationStage;

    /** 响应类型，例如 MEAL_PLAN_DIAGNOSIS 或 BUSINESS_QUERY。 */
    private String responseType;

    /** 受控业务查询的结构化结果，禁止存放金额字段。 */
    private Map<String, Object> insightResult = new LinkedHashMap<>();

    /** 可追溯事实，前端直接展示而不解析自然语言。 */
    private List<Map<String, Object>> facts = new ArrayList<>();

    /** 部分成功、截断和权限不足等展示告警。 */
    private List<String> warnings = new ArrayList<>();

    /** 是否命中同一轮业务查询缓存。 */
    private boolean cached;

    /** 是否仅返回部分业务查询结果。 */
    private boolean partial;

    /** 主系统时区下的查询时间。 */
    private String queriedAt;

    /** agent-service 生成并校验后的受控查询计划摘要。 */
    private Map<String, Object> queryPlan = new LinkedHashMap<>();
    /** Agent 返回的待补条件受控上下文，由主系统原样持久化但不允许前端构造。 */
    private Map<String, Object> pendingBusinessQueryContext;
    /** Agent 返回的最近业务查询脱敏摘要。 */
    private Map<String, Object> lastBusinessQueryContext;
    /** 不含问题原文、Prompt 和业务结果的语义追踪摘要。 */
    private Map<String, Object> semanticTraceSummary;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public Map<String, String> getSlotConfidence() {
        return slotConfidence;
    }

    public void setSlotConfidence(Map<String, String> slotConfidence) {
        this.slotConfidence = slotConfidence;
    }

    public List<String> getMissingSlots() {
        return missingSlots;
    }

    public void setMissingSlots(List<String> missingSlots) {
        this.missingSlots = missingSlots;
    }

    public AgentDiagnosisResponse getDiagnosisResult() {
        return diagnosisResult;
    }

    public void setDiagnosisResult(AgentDiagnosisResponse diagnosisResult) {
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

    public String getResponseType() { return responseType; }
    public void setResponseType(String responseType) { this.responseType = responseType; }
    public Map<String, Object> getInsightResult() { return insightResult; }
    public void setInsightResult(Map<String, Object> insightResult) { this.insightResult = insightResult; }
    public List<Map<String, Object>> getFacts() { return facts; }
    public void setFacts(List<Map<String, Object>> facts) { this.facts = facts; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached; }
    public boolean isPartial() { return partial; }
    public void setPartial(boolean partial) { this.partial = partial; }
    public String getQueriedAt() { return queriedAt; }
    public void setQueriedAt(String queriedAt) { this.queriedAt = queriedAt; }
    public Map<String, Object> getQueryPlan() { return queryPlan; }
    public void setQueryPlan(Map<String, Object> queryPlan) { this.queryPlan = queryPlan; }
    public Map<String, Object> getPendingBusinessQueryContext() { return pendingBusinessQueryContext; }
    public void setPendingBusinessQueryContext(Map<String, Object> pendingBusinessQueryContext) { this.pendingBusinessQueryContext = pendingBusinessQueryContext; }
    public Map<String, Object> getLastBusinessQueryContext() { return lastBusinessQueryContext; }
    public void setLastBusinessQueryContext(Map<String, Object> lastBusinessQueryContext) { this.lastBusinessQueryContext = lastBusinessQueryContext; }
    public Map<String, Object> getSemanticTraceSummary() { return semanticTraceSummary; }
    public void setSemanticTraceSummary(Map<String, Object> semanticTraceSummary) { this.semanticTraceSummary = semanticTraceSummary; }
}
