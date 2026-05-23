package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 排餐诊断聊天会话。
 */
public class MealPlanChatSession {

    private String sessionId;
    private DiagnosisSlots slots = new DiagnosisSlots();
    private String lastUserMessage;
    private DiagnosisResponse lastDiagnosisResult;
    private List<String> recentMessages = new ArrayList<>();
    private Instant updatedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public DiagnosisSlots getSlots() {
        return slots;
    }

    public void setSlots(DiagnosisSlots slots) {
        this.slots = slots;
    }

    public String getLastUserMessage() {
        return lastUserMessage;
    }

    public void setLastUserMessage(String lastUserMessage) {
        this.lastUserMessage = lastUserMessage;
    }

    public DiagnosisResponse getLastDiagnosisResult() {
        return lastDiagnosisResult;
    }

    public void setLastDiagnosisResult(DiagnosisResponse lastDiagnosisResult) {
        this.lastDiagnosisResult = lastDiagnosisResult;
    }

    public List<String> getRecentMessages() {
        return recentMessages;
    }

    public void setRecentMessages(List<String> recentMessages) {
        this.recentMessages = recentMessages;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
