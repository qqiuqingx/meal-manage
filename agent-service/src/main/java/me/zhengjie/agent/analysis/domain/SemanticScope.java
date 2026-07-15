package me.zhengjie.agent.analysis.domain;

/** 语义帧的数据范围；具体句柄必须由服务端解析后绑定。 */
public class SemanticScope {
    public enum Type { EXPLICIT, SESSION_FOCUS, CONTEXT_REFERENCE, AUTHORIZED_SCOPE }
    private Type type;
    private ContextHandleKind requiredKind;
    private SemanticEntityType requiredEntityType;
    private String resolvedHandleId;
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public ContextHandleKind getRequiredKind() { return requiredKind; }
    public void setRequiredKind(ContextHandleKind requiredKind) { this.requiredKind = requiredKind; }
    public SemanticEntityType getRequiredEntityType() { return requiredEntityType; }
    public void setRequiredEntityType(SemanticEntityType requiredEntityType) { this.requiredEntityType = requiredEntityType; }
    public String getResolvedHandleId() { return resolvedHandleId; }
    public void setResolvedHandleId(String resolvedHandleId) { this.resolvedHandleId = resolvedHandleId; }
}
