package me.zhengjie.agent.domain.dto;

import me.zhengjie.agent.observability.DiagnosisTraceEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能排查的标准输出，包含摘要、原因列表和基础上下文。
 */
public class DiagnosisResponse {

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
    private List<String> nextActions = new ArrayList<>();
    private List<DiagnosisTraceEvent> diagnosisTrace = new ArrayList<>();
    private List<DiagnosisTraceEvent> toolCallSummary = new ArrayList<>();
    private List<DiagnosisReasonDto> reasons = new ArrayList<>();
    private List<DiagnosisActionDraftDto> actionDrafts = new ArrayList<>();

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

    public List<String> getNextActions() {
        return nextActions;
    }

    public void setNextActions(List<String> nextActions) {
        this.nextActions = nextActions;
    }

    public List<DiagnosisTraceEvent> getDiagnosisTrace() {
        return diagnosisTrace;
    }

    public void setDiagnosisTrace(List<DiagnosisTraceEvent> diagnosisTrace) {
        this.diagnosisTrace = diagnosisTrace;
    }

    public List<DiagnosisTraceEvent> getToolCallSummary() {
        return toolCallSummary;
    }

    public void setToolCallSummary(List<DiagnosisTraceEvent> toolCallSummary) {
        this.toolCallSummary = toolCallSummary;
    }

    public List<DiagnosisReasonDto> getReasons() {
        return reasons;
    }

    public void setReasons(List<DiagnosisReasonDto> reasons) {
        this.reasons = reasons;
    }

    public List<DiagnosisActionDraftDto> getActionDrafts() {
        return actionDrafts;
    }

    public void setActionDrafts(List<DiagnosisActionDraftDto> actionDrafts) {
        this.actionDrafts = actionDrafts;
    }
}
