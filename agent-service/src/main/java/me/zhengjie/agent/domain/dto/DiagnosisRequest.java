package me.zhengjie.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 智能排查的输入参数，承载客户、日期和餐次信息。
 */
public class DiagnosisRequest {

    private Long customerId;
    private String customerCode;

    @NotBlank
    private String recordDate;

    @NotBlank
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
}
