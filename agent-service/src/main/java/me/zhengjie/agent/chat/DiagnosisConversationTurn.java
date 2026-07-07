package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;

import java.time.Instant;

/**
 * 单轮会话快照，记录消息角色、内容、槽位和关联诊断请求。
 */
public class DiagnosisConversationTurn {

    private String role;
    private String message;
    private DiagnosisSlots slotsSnapshot;
    private String intent;
    private Instant createdAt;
    private String diagnosisRequestId;

    /**
     * 创建用户消息轮次。
     *
     * @param message 会话消息
     * @param slotsSnapshot 当前槽位快照
     * @param intent 当前识别到的意图
     * @return 用户轮次对象
     */
    public static DiagnosisConversationTurn userTurn(String message, DiagnosisSlots slotsSnapshot, String intent) {
        DiagnosisConversationTurn turn = new DiagnosisConversationTurn();
        turn.setRole("user");
        turn.setMessage(message);
        turn.setSlotsSnapshot(slotsSnapshot);
        turn.setIntent(intent);
        turn.setCreatedAt(Instant.now());
        return turn;
    }

    /**
     * 创建助手消息轮次。
     *
     * @param message 助手回复
     * @param slotsSnapshot 当前槽位快照
     * @param intent 对应的会话意图
     * @param diagnosisRequestId 关联的诊断请求标识
     * @return 助手轮次对象
     */
    public static DiagnosisConversationTurn assistantTurn(String message, DiagnosisSlots slotsSnapshot, String intent, String diagnosisRequestId) {
        DiagnosisConversationTurn turn = new DiagnosisConversationTurn();
        turn.setRole("assistant");
        turn.setMessage(message);
        turn.setSlotsSnapshot(slotsSnapshot);
        turn.setIntent(intent);
        turn.setCreatedAt(Instant.now());
        turn.setDiagnosisRequestId(diagnosisRequestId);
        return turn;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public DiagnosisSlots getSlotsSnapshot() {
        return slotsSnapshot;
    }

    public void setSlotsSnapshot(DiagnosisSlots slotsSnapshot) {
        this.slotsSnapshot = slotsSnapshot;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getDiagnosisRequestId() {
        return diagnosisRequestId;
    }

    public void setDiagnosisRequestId(String diagnosisRequestId) {
        this.diagnosisRequestId = diagnosisRequestId;
    }
}
