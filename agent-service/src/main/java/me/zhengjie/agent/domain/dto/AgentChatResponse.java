package me.zhengjie.agent.domain.dto;

import java.util.ArrayList;
import java.util.List;

import me.zhengjie.agent.domain.chat.ChatStatus;

/**
 * 智能排查聊天响应。
 */
public class AgentChatResponse {

    private String sessionId;
    private ChatStatus status;
    private String assistantMessage;
    private DiagnosisSlots slots;
    private DiagnosisResponse diagnosisResult;
    private List<String> quickReplies = new ArrayList<>();

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
}
