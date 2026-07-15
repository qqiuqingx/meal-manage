package me.zhengjie.agent.query.domain;

/** 业务语义分析的脱敏追踪摘要，不包含用户原文、Prompt 或模型原始输出。 */
public class SemanticTraceSummary {
    private String semanticSource;
    private String fallbackReason;
    private Double semanticConfidence;
    private String semanticCatalogVersion;
    private String temporalExpression;
    private String resolvedRecordDate;
    private String resolvedStartDate;
    private String resolvedEndDate;
    private boolean pendingContextReused;
    private String interactionMode;
    private String referenceResolution;
    private String confidenceBucket;

    public String getSemanticSource() { return semanticSource; }
    public void setSemanticSource(String semanticSource) { this.semanticSource = semanticSource; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public Double getSemanticConfidence() { return semanticConfidence; }
    public void setSemanticConfidence(Double semanticConfidence) { this.semanticConfidence = semanticConfidence; }
    public String getSemanticCatalogVersion() { return semanticCatalogVersion; }
    public void setSemanticCatalogVersion(String semanticCatalogVersion) { this.semanticCatalogVersion = semanticCatalogVersion; }
    public String getTemporalExpression() { return temporalExpression; }
    public void setTemporalExpression(String temporalExpression) { this.temporalExpression = temporalExpression; }
    public String getResolvedRecordDate() { return resolvedRecordDate; }
    public void setResolvedRecordDate(String resolvedRecordDate) { this.resolvedRecordDate = resolvedRecordDate; }
    public String getResolvedStartDate() { return resolvedStartDate; }
    public void setResolvedStartDate(String resolvedStartDate) { this.resolvedStartDate = resolvedStartDate; }
    public String getResolvedEndDate() { return resolvedEndDate; }
    public void setResolvedEndDate(String resolvedEndDate) { this.resolvedEndDate = resolvedEndDate; }
    public boolean isPendingContextReused() { return pendingContextReused; }
    public void setPendingContextReused(boolean pendingContextReused) { this.pendingContextReused = pendingContextReused; }
    public String getInteractionMode() { return interactionMode; }
    public void setInteractionMode(String interactionMode) { this.interactionMode = interactionMode; }
    public String getReferenceResolution() { return referenceResolution; }
    public void setReferenceResolution(String referenceResolution) { this.referenceResolution = referenceResolution; }
    public String getConfidenceBucket() { return confidenceBucket; }
    public void setConfidenceBucket(String confidenceBucket) { this.confidenceBucket = confidenceBucket; }
}
