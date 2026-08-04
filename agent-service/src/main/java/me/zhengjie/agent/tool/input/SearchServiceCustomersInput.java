package me.zhengjie.agent.tool.input;

/** 以订单为根查询服务客户的强类型输入。 */
public class SearchServiceCustomersInput {
    private Long customerId;
    private String customerCode;
    private Long orderId;
    private String orderCode;
    private ServiceCustomerStatus status = ServiceCustomerStatus.ALL;
    private String dealTimeFrom;
    private String dealTimeTo;
    private String packageCode;
    private Integer page = 1;
    private Integer size = 20;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String value) { customerCode = value; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long value) { orderId = value; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String value) { orderCode = value; }
    public ServiceCustomerStatus getStatus() { return status; }
    public void setStatus(ServiceCustomerStatus value) { status = value == null ? ServiceCustomerStatus.ALL : value; }
    public String getDealTimeFrom() { return dealTimeFrom; }
    public void setDealTimeFrom(String value) { dealTimeFrom = value; }
    public String getDealTimeTo() { return dealTimeTo; }
    public void setDealTimeTo(String value) { dealTimeTo = value; }
    public String getPackageCode() { return packageCode; }
    public void setPackageCode(String value) { packageCode = value; }
    public Integer getPage() { return page; }
    public void setPage(Integer value) { page = value; }
    public Integer getSize() { return size; }
    public void setSize(Integer value) { size = value; }
}
