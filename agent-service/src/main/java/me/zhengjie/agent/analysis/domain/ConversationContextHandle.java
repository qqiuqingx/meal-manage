package me.zhengjie.agent.analysis.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 不携带原始工具结果、可重新计算的会话上下文句柄。 */
public class ConversationContextHandle {
    private String handleId;
    private ContextHandleKind kind;
    private SemanticEntityType entityType;
    private String definitionId;
    private String sourceTurnId;
    private String sourceRequestId;
    private Integer cardinality;
    private Map<String, Object> safeDescriptor = new LinkedHashMap<>();
    private List<SemanticOperation> allowedOperations = new ArrayList<>();
    private double salience;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    public String getHandleId() { return handleId; }
    public void setHandleId(String handleId) { this.handleId = handleId; }
    public ContextHandleKind getKind() { return kind; }
    public void setKind(ContextHandleKind kind) { this.kind = kind; }
    public SemanticEntityType getEntityType() { return entityType; }
    public void setEntityType(SemanticEntityType entityType) { this.entityType = entityType; }
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
    public String getSourceTurnId() { return sourceTurnId; }
    public void setSourceTurnId(String sourceTurnId) { this.sourceTurnId = sourceTurnId; }
    public String getSourceRequestId() { return sourceRequestId; }
    public void setSourceRequestId(String sourceRequestId) { this.sourceRequestId = sourceRequestId; }
    public Integer getCardinality() { return cardinality; }
    public void setCardinality(Integer cardinality) { this.cardinality = cardinality; }
    public Map<String, Object> getSafeDescriptor() { return safeDescriptor; }
    public void setSafeDescriptor(Map<String, Object> safeDescriptor) { this.safeDescriptor = safeDescriptor == null ? new LinkedHashMap<>() : safeDescriptor; }
    public List<SemanticOperation> getAllowedOperations() { return allowedOperations; }
    public void setAllowedOperations(List<SemanticOperation> allowedOperations) { this.allowedOperations = allowedOperations == null ? new ArrayList<>() : allowedOperations; }
    public double getSalience() { return salience; }
    public void setSalience(double salience) { this.salience = salience; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
}
