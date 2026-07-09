package me.zhengjie.agent.domain.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.chat.ChatStatus;

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
