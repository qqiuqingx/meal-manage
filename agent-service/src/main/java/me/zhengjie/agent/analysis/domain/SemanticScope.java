package me.zhengjie.agent.analysis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** 语义帧的数据范围；具体句柄必须由服务端解析后绑定。 */
public class SemanticScope {
    public enum Type { EXPLICIT, SESSION_FOCUS, CONTEXT_REFERENCE, AUTHORIZED_SCOPE }
    private Type type;
    private ContextHandleKind requiredKind;
    private SemanticEntityType requiredEntityType;
    private String resolvedHandleId;
    private String resolvedDefinitionId;
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public ContextHandleKind getRequiredKind() { return requiredKind; }
    public void setRequiredKind(ContextHandleKind requiredKind) { this.requiredKind = requiredKind; }
    public SemanticEntityType getRequiredEntityType() { return requiredEntityType; }
    public void setRequiredEntityType(SemanticEntityType requiredEntityType) { this.requiredEntityType = requiredEntityType; }
    /** 服务端解析结果，不属于模型协议，也不得由外部请求直接提供。 */
    @JsonIgnore
    public String getResolvedHandleId() { return resolvedHandleId; }
    public void setResolvedHandleId(String resolvedHandleId) { this.resolvedHandleId = resolvedHandleId; }
    /** 服务端绑定的集合定义，用于能力目录限制可重算范围，不属于模型协议。 */
    @JsonIgnore
    public String getResolvedDefinitionId() { return resolvedDefinitionId; }
    public void setResolvedDefinitionId(String resolvedDefinitionId) { this.resolvedDefinitionId = resolvedDefinitionId; }
}
