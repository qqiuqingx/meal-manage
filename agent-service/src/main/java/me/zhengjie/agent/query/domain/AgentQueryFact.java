package me.zhengjie.agent.query.domain;

/**
 * 业务查询回答的可追溯结构化事实。
 */
public class AgentQueryFact {
    private String factId;
    private String label;
    private Object value;
    private String unit;
    private String sourceType;
    private String sourceId;

    public AgentQueryFact() { }
    public AgentQueryFact(String factId, String label, Object value, String unit, String sourceType, String sourceId) {
        this.factId = factId; this.label = label; this.value = value; this.unit = unit; this.sourceType = sourceType; this.sourceId = sourceId;
    }
    public String getFactId() { return factId; }
    public void setFactId(String factId) { this.factId = factId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
}
