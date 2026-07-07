package me.zhengjie.agent.observability;

import java.time.Instant;

/**
 * 单条诊断链路观测事件，既可用于工具摘要，也可用于完整 trace 展示。
 */
public class DiagnosisTraceEvent {

    private String eventType;
    private String toolName;
    private String inputDigest;
    private boolean success;
    private boolean cached;
    private int resultCount;
    private long costMs;
    private String errorType;
    private String errorMessage;
    private Integer round;
    private String toolNames;
    private Instant createdAt = Instant.now();

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getInputDigest() {
        return inputDigest;
    }

    public void setInputDigest(String inputDigest) {
        this.inputDigest = inputDigest;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }

    public int getResultCount() {
        return resultCount;
    }

    public void setResultCount(int resultCount) {
        this.resultCount = resultCount;
    }

    public long getCostMs() {
        return costMs;
    }

    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRound() {
        return round;
    }

    public void setRound(Integer round) {
        this.round = round;
    }

    public String getToolNames() {
        return toolNames;
    }

    public void setToolNames(String toolNames) {
        this.toolNames = toolNames;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
