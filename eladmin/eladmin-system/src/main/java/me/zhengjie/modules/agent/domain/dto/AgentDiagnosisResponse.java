package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 后台展示的智能排查结果。
 */
@Data
public class AgentDiagnosisResponse {

    private String requestId;

    private Long customerId;

    private String customerName;

    private String recordDate;

    private String mealType;

    private String summary;

    private String ruleVersionDigest;

    private String modelName;

    private String confidence;

    private boolean fallback;

    private String fallbackReason;

    private String fallbackSource;

    private String failureType;

    private List<String> nextActions = new ArrayList<>();

    private List<Map<String, Object>> diagnosisTrace = new ArrayList<>();

    private List<Map<String, Object>> toolCallSummary = new ArrayList<>();

    private List<AgentDiagnosisReasonDto> reasons = new ArrayList<>();

    private List<AgentDiagnosisActionDraftDto> actionDrafts = new ArrayList<>();

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRuleVersionDigest() {
        return ruleVersionDigest;
    }

    public void setRuleVersionDigest(String ruleVersionDigest) {
        this.ruleVersionDigest = ruleVersionDigest;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public boolean isFallback() {
        return fallback;
    }

    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getFallbackSource() {
        return fallbackSource;
    }

    public void setFallbackSource(String fallbackSource) {
        this.fallbackSource = fallbackSource;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public List<String> getNextActions() {
        return nextActions;
    }

    public void setNextActions(List<String> nextActions) {
        this.nextActions = nextActions;
    }

    public List<Map<String, Object>> getDiagnosisTrace() {
        return diagnosisTrace;
    }

    public void setDiagnosisTrace(List<Map<String, Object>> diagnosisTrace) {
        this.diagnosisTrace = diagnosisTrace;
    }

    public List<Map<String, Object>> getToolCallSummary() {
        return toolCallSummary;
    }

    public void setToolCallSummary(List<Map<String, Object>> toolCallSummary) {
        this.toolCallSummary = toolCallSummary;
    }

    public List<AgentDiagnosisReasonDto> getReasons() {
        return reasons;
    }

    public void setReasons(List<AgentDiagnosisReasonDto> reasons) {
        this.reasons = reasons;
    }

    public List<AgentDiagnosisActionDraftDto> getActionDrafts() {
        return actionDrafts;
    }

    public void setActionDrafts(List<AgentDiagnosisActionDraftDto> actionDrafts) {
        this.actionDrafts = actionDrafts;
    }
}
