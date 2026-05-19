package me.zhengjie.agent.domain.dto;

public class DiagnosisToolCustomerLookupRequest {

    private Long customerId;
    private String customerCode;

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
}
