package me.zhengjie.modules.agent.session.domain.dto;

import lombok.Data;

/**
 * 智能排查会话创建请求。
 */
@Data
public class AgentChatSessionCreateRequest {

    private Long customerId;

    private String customerCode;

    private String recordDate;

    private String mealType;

    private String title;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
