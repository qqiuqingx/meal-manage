package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;

import java.time.Instant;

/**
 * 排餐诊断聊天会话。
 */
public class MealPlanChatSession {

    private String sessionId;
    private DiagnosisSlots slots = new DiagnosisSlots();
    private DiagnosisConversationState conversationState = DiagnosisConversationState.initialize();
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

    public DiagnosisConversationState getConversationState() {
        return conversationState;
    }

    public void setConversationState(DiagnosisConversationState conversationState) {
        this.conversationState = conversationState;
    }

    /**
     * 兼容现有调用，返回最近一次可复用的诊断结果。
     *
     * @return 最近一次诊断结果
     */
    public DiagnosisResponse getLastDiagnosisResult() {
        return conversationState == null ? null : conversationState.getLastDiagnosisResult();
    }

    /**
     * 兼容现有调用，更新最近一次可复用的诊断结果。
     *
     * @param lastDiagnosisResult 最近一次诊断结果
     */
    public void setLastDiagnosisResult(DiagnosisResponse lastDiagnosisResult) {
        if (conversationState == null) {
            conversationState = DiagnosisConversationState.initialize();
        }
        conversationState.setLastDiagnosisResult(lastDiagnosisResult);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
