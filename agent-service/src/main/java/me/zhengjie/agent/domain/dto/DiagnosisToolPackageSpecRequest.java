package me.zhengjie.agent.domain.dto;

public class DiagnosisToolPackageSpecRequest {

    private Long customerId;
    private String customerCode;
    private Long parentPackageId;
    private Long childPackageId;

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

    public Long getParentPackageId() {
        return parentPackageId;
    }

    public void setParentPackageId(Long parentPackageId) {
        this.parentPackageId = parentPackageId;
    }

    public Long getChildPackageId() {
        return childPackageId;
    }

    public void setChildPackageId(Long childPackageId) {
        this.childPackageId = childPackageId;
    }
}
