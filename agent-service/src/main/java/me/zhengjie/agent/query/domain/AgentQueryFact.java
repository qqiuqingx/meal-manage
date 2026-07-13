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
    /** 客户业务编号；客户相关事实禁止使用内部客户 ID 替代。 */
    private String customerCode;
    /** 事实对应的业务日期。 */
    private String recordDate;
    /** 事实对应的餐次。 */
    private String mealType;
    /** 内部溯源记录 ID，不作为客户身份展示。 */
    private String sourceRecordId;

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
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String recordDate) { this.recordDate = recordDate; }
    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
}
