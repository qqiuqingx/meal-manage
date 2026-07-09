package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天诊断会话槽位。
 */
@Data
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
}
