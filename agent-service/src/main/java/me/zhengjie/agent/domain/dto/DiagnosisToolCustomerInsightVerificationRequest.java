package me.zhengjie.agent.domain.dto;

/**
 * 客户核销统计查询请求
 * @author qqx
 * @date 2026-07-09
 */
public class DiagnosisToolCustomerInsightVerificationRequest {

    private Long customerId;
    private String customerCode;
    private String mealType;
    private String recordDateStart;
    private String recordDateEnd;
    private Integer recentLimit;

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

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
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

    public Integer getRecentLimit() {
        return recentLimit;
    }

    public void setRecentLimit(Integer recentLimit) {
        this.recentLimit = recentLimit;
    }
}
