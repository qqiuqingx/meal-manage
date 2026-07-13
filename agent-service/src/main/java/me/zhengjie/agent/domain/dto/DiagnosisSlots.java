package me.zhengjie.agent.domain.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话中的诊断槽位。
 */
public class DiagnosisSlots {

    private Long customerId;
    private String customerCode;
    private String customerName;
    private String recordDate;
    /** 受控查询起始日期（yyyy-MM-dd），用于核销和退餐范围查询。 */
    private String startDate;
    /** 受控查询结束日期（yyyy-MM-dd），用于核销和退餐范围查询。 */
    private String endDate;
    private String mealType;
    private Integer orderStatus;
    private Long orderId;
    private String orderCode;
    /** 当前会话聚焦的排餐客户记录 ID。 */
    private Long mealPlanRecordId;
    private Map<String, String> slotConfidence = new LinkedHashMap<>();
    private Map<String, String> slotSource = new LinkedHashMap<>();

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(String recordDate) {
        this.recordDate = recordDate;
    }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public Long getMealPlanRecordId() { return mealPlanRecordId; }
    public void setMealPlanRecordId(Long mealPlanRecordId) { this.mealPlanRecordId = mealPlanRecordId; }

    public Map<String, String> getSlotConfidence() {
        return slotConfidence;
    }

    public void setSlotConfidence(Map<String, String> slotConfidence) {
        this.slotConfidence = slotConfidence;
    }

    public Map<String, String> getSlotSource() {
        return slotSource;
    }

    public void setSlotSource(Map<String, String> slotSource) {
        this.slotSource = slotSource;
    }

    /**
     * 设置客户槽位的置信度。
     *
     * @param confidence 置信度等级
     */
    public void setCustomerConfidence(String confidence) {
        put(slotConfidence, "customer", confidence);
    }

    /**
     * 设置日期槽位的置信度。
     *
     * @param confidence 置信度等级
     */
    public void setRecordDateConfidence(String confidence) {
        put(slotConfidence, "recordDate", confidence);
    }

    /**
     * 设置餐次槽位的置信度。
     *
     * @param confidence 置信度等级
     */
    public void setMealTypeConfidence(String confidence) {
        put(slotConfidence, "mealType", confidence);
    }

    /**
     * 设置客户槽位的来源。
     *
     * @param source 来源标识
     */
    public void setCustomerSource(String source) {
        put(slotSource, "customer", source);
    }

    /**
     * 设置日期槽位的来源。
     *
     * @param source 来源标识
     */
    public void setRecordDateSource(String source) {
        put(slotSource, "recordDate", source);
    }

    /**
     * 设置餐次槽位的来源。
     *
     * @param source 来源标识
     */
    public void setMealTypeSource(String source) {
        put(slotSource, "mealType", source);
    }

    public String getCustomerConfidence() {
        return slotConfidence.get("customer");
    }

    public String getRecordDateConfidence() {
        return slotConfidence.get("recordDate");
    }

    public String getMealTypeConfidence() {
        return slotConfidence.get("mealType");
    }

    public String getCustomerSource() {
        return slotSource.get("customer");
    }

    public String getRecordDateSource() {
        return slotSource.get("recordDate");
    }

    public String getMealTypeSource() {
        return slotSource.get("mealType");
    }

    private void put(Map<String, String> target, String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            target.remove(key);
            return;
        }
        target.put(key, value);
    }
}
