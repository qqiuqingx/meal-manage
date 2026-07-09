package me.zhengjie.agent.domain.dto;

/**
 * 客户订单列表查询请求
 * @author qqx
 * @date 2026-07-09
 */
public class DiagnosisToolCustomerInsightOrderRequest {

    private Long customerId;
    private String customerCode;
    private Integer orderStatus;

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

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }
}
