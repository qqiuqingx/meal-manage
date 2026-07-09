package me.zhengjie.modules.agent.session.domain.dto;

import lombok.Data;
import me.zhengjie.modules.agent.domain.AgentActionAudit;
import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.domain.dto.DiagnosisSlots;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能排查会话详情。
 */
@Data
public class AgentChatSessionDetailDto {

    private String sessionId;

    private String title;

    private String operator;

    private Long customerId;

    private String customerCode;

    private String recordDate;

    private String mealType;

    private String stage;

    private String lastRequestId;

    private String lastSummary;

    private Timestamp lastMessageTime;

    private Boolean archived;

    private Timestamp createTime;

    private Timestamp updateTime;

    private DiagnosisSlots currentSlots;

    private AgentDiagnosisResponse latestDiagnosisResult;

    private List<AgentChatMessageDto> messages = new ArrayList<>();

    private List<AgentActionAudit> recentAudits = new ArrayList<>();

    private List<AgentDiagnosisFeedback> recentFeedbacks = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(String recordDate) {
        this.recordDate = recordDate;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getLastRequestId() {
        return lastRequestId;
    }

    public void setLastRequestId(String lastRequestId) {
        this.lastRequestId = lastRequestId;
    }

    public String getLastSummary() {
        return lastSummary;
    }

    public void setLastSummary(String lastSummary) {
        this.lastSummary = lastSummary;
    }

    public Timestamp getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Timestamp lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public Timestamp getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Timestamp updateTime) {
        this.updateTime = updateTime;
    }

    public DiagnosisSlots getCurrentSlots() {
        return currentSlots;
    }

    public void setCurrentSlots(DiagnosisSlots currentSlots) {
        this.currentSlots = currentSlots;
    }

    public AgentDiagnosisResponse getLatestDiagnosisResult() {
        return latestDiagnosisResult;
    }

    public void setLatestDiagnosisResult(AgentDiagnosisResponse latestDiagnosisResult) {
        this.latestDiagnosisResult = latestDiagnosisResult;
    }

    public List<AgentChatMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<AgentChatMessageDto> messages) {
        this.messages = messages;
    }

    public List<AgentActionAudit> getRecentAudits() {
        return recentAudits;
    }

    public void setRecentAudits(List<AgentActionAudit> recentAudits) {
        this.recentAudits = recentAudits;
    }

    public List<AgentDiagnosisFeedback> getRecentFeedbacks() {
        return recentFeedbacks;
    }

    public void setRecentFeedbacks(List<AgentDiagnosisFeedback> recentFeedbacks) {
        this.recentFeedbacks = recentFeedbacks;
    }
}
