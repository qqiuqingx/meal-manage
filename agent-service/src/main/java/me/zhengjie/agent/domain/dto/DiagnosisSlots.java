package me.zhengjie.agent.domain.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话中的诊断槽位。
 */
public class DiagnosisSlots {

    private Long customerId;
    private String customerCode;
    private String recordDate;
    private String mealType;
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
