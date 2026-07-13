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
    private String customerName;
    private String recordDate;
    /** 受控查询起始日期（yyyy-MM-dd）。 */
    private String startDate;
    /** 受控查询结束日期（yyyy-MM-dd）。 */
    private String endDate;
    private String mealType;
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
}
