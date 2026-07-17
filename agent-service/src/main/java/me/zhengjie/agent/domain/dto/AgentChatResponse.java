package me.zhengjie.agent.domain.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.query.domain.AgentQueryFact;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;
import me.zhengjie.agent.query.domain.SemanticTraceSummary;
import me.zhengjie.agent.query.domain.ConversationTaskStack;
import me.zhengjie.agent.query.domain.BusinessQueryResultBlock;

/**
 * 智能排查聊天响应。
 */
public class AgentChatResponse {

    private String requestId;
    private String sessionId;
    private ChatStatus status;
    private String assistantMessage;
    private DiagnosisSlots slots;
    private Map<String, String> slotConfidence = new LinkedHashMap<>();
    private List<MissingSlot> missingSlots = new ArrayList<>();
    private DiagnosisResponse diagnosisResult;
    private List<String> quickReplies = new ArrayList<>();
    private String conversationStage;

    // 客户信息查询响应
    private String responseType;
    private Map<String, Object> insightResult = new LinkedHashMap<>();
    private List<AgentQueryFact> facts = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean cached;
    private boolean partial;
    private String queriedAt;
    private AgentQueryPlan queryPlan;
    private PendingBusinessQueryContext pendingBusinessQueryContext;
    private LastBusinessQueryContext lastBusinessQueryContext;
    private SemanticTraceSummary semanticTraceSummary;
    private ConversationTaskStack activeTaskStack;
    private List<BusinessQueryResultBlock> resultBlocks = new ArrayList<>();

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public Map<String, Object> getInsightResult() {
        return insightResult;
    }

    public void setInsightResult(Map<String, Object> insightResult) {
        this.insightResult = insightResult;
    }
    public List<AgentQueryFact> getFacts() { return facts; }
    public void setFacts(List<AgentQueryFact> facts) { this.facts = facts; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached; }
    public boolean isPartial() { return partial; }
    public void setPartial(boolean partial) { this.partial = partial; }
    public String getQueriedAt() { return queriedAt; }
    public void setQueriedAt(String queriedAt) { this.queriedAt = queriedAt; }
    public AgentQueryPlan getQueryPlan() { return queryPlan; }
    public void setQueryPlan(AgentQueryPlan queryPlan) { this.queryPlan = queryPlan; }
    public PendingBusinessQueryContext getPendingBusinessQueryContext() { return pendingBusinessQueryContext; }
    public void setPendingBusinessQueryContext(PendingBusinessQueryContext pendingBusinessQueryContext) { this.pendingBusinessQueryContext = pendingBusinessQueryContext; }
    public LastBusinessQueryContext getLastBusinessQueryContext() { return lastBusinessQueryContext; }
    public void setLastBusinessQueryContext(LastBusinessQueryContext lastBusinessQueryContext) { this.lastBusinessQueryContext = lastBusinessQueryContext; }
    public SemanticTraceSummary getSemanticTraceSummary() { return semanticTraceSummary; }
    public void setSemanticTraceSummary(SemanticTraceSummary semanticTraceSummary) { this.semanticTraceSummary = semanticTraceSummary; }
    public ConversationTaskStack getActiveTaskStack() { return activeTaskStack; }
    public void setActiveTaskStack(ConversationTaskStack activeTaskStack) { this.activeTaskStack = activeTaskStack; }
    public List<BusinessQueryResultBlock> getResultBlocks() { return resultBlocks; }
    public void setResultBlocks(List<BusinessQueryResultBlock> resultBlocks) { this.resultBlocks = resultBlocks == null ? new ArrayList<>() : resultBlocks; }

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

    public Map<String, String> getSlotConfidence() {
        return slotConfidence;
    }

    public void setSlotConfidence(Map<String, String> slotConfidence) {
        this.slotConfidence = slotConfidence;
    }

    public List<MissingSlot> getMissingSlots() {
        return missingSlots;
    }

    public void setMissingSlots(List<MissingSlot> missingSlots) {
        this.missingSlots = missingSlots;
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
