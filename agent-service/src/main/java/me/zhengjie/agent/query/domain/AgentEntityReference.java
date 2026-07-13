package me.zhengjie.agent.query.domain;

/**
 * QueryPlan 中允许出现的稳定业务对象标识，不承载任意查询字段。
 */
public class AgentEntityReference {

    private Long customerId;
    private String customerCode;
    private String customerName;
    private Long orderId;
    private String orderCode;
    private Long mealPlanRecordId;
    private Long packageId;
    private Long dishId;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public Long getMealPlanRecordId() { return mealPlanRecordId; }
    public void setMealPlanRecordId(Long mealPlanRecordId) { this.mealPlanRecordId = mealPlanRecordId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Long getDishId() { return dishId; }
    public void setDishId(Long dishId) { this.dishId = dishId; }
}
