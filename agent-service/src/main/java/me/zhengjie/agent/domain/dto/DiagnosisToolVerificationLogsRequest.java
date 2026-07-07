package me.zhengjie.agent.domain.dto;

public class DiagnosisToolVerificationLogsRequest {

    private Long customerId;
    private String customerCode;
    private Long orderId;
    private String recordDateStart;
    private String recordDateEnd;
    private String mealType;

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

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getRecordDateStart() {
        return recordDateStart;
    }

    public void setRecordDateStart(String recordDateStart) {
        this.recordDateStart = recordDateStart;
    }

    public String getRecordDateEnd() {
        return recordDateEnd;
    }

    public void setRecordDateEnd(String recordDateEnd) {
        this.recordDateEnd = recordDateEnd;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }
}
