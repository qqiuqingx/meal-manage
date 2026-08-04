package me.zhengjie.agent.tool.input;

/** 查询客户档案的强类型输入；不包含权限、数据范围或内部鉴权字段。 */
public class SearchCustomerProfilesInput {
    private Long customerId;
    private String customerCode;
    private String customerName;
    private Boolean hasOrder;
    private Integer page = 1;
    private Integer size = 20;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String value) { customerCode = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    public Boolean getHasOrder() { return hasOrder; }
    public void setHasOrder(Boolean value) { hasOrder = value; }
    public Integer getPage() { return page; }
    public void setPage(Integer value) { page = value; }
    public Integer getSize() { return size; }
    public void setSize(Integer value) { size = value; }
}
