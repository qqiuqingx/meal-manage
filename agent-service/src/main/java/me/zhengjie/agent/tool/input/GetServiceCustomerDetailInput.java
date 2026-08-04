package me.zhengjie.agent.tool.input;

/** 查询单个客户或订单综合快照的强类型输入。 */
public class GetServiceCustomerDetailInput {
    private Long customerId;
    private String customerCode;
    private Long orderId;
    private String orderCode;
    private DetailLevel detailLevel = DetailLevel.STANDARD;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String value) { customerCode = value; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long value) { orderId = value; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String value) { orderCode = value; }
    public DetailLevel getDetailLevel() { return detailLevel; }
    public void setDetailLevel(DetailLevel value) { detailLevel = value == null ? DetailLevel.STANDARD : value; }

    /** 详情级别由服务端解释，模型不能提交任意返回字段白名单。 */
    public enum DetailLevel { STANDARD, DIAGNOSTIC }
}
