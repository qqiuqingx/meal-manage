package me.zhengjie.agent.domain.dto;

/**
 * 客户餐数汇总查询请求
 * @author qqx
 * @date 2026-07-09
 */
public class DiagnosisToolCustomerInsightMealRequest {

    private Long customerId;
    private String customerCode;
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

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }
}
