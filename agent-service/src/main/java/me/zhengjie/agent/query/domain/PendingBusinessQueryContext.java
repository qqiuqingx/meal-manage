package me.zhengjie.agent.query.domain;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 可跨轮持久化的待执行业务查询。仅保存受控语义、缺失字段和限长摘要，不保存模型原文或工具结果。
 */
public class PendingBusinessQueryContext {
    private BusinessQuestionAnalysis analysis;
    private List<String> missingFields = new ArrayList<>();
    private String originalQuestionSummary;
    private String sourceRequestId;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;

    public BusinessQuestionAnalysis getAnalysis() { return analysis; }
    public void setAnalysis(BusinessQuestionAnalysis analysis) { this.analysis = analysis; }
    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }
    public String getOriginalQuestionSummary() { return originalQuestionSummary; }
    public void setOriginalQuestionSummary(String originalQuestionSummary) { this.originalQuestionSummary = originalQuestionSummary; }
    public String getSourceRequestId() { return sourceRequestId; }
    public void setSourceRequestId(String sourceRequestId) { this.sourceRequestId = sourceRequestId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    /** 判断上下文在指定业务时间是否已过期。 */
    public boolean isExpired(OffsetDateTime now) { return expiresAt != null && now != null && !expiresAt.isAfter(now); }
}
