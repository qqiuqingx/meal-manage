package me.zhengjie.agent.tool.input;

/** 查询客户指定日期餐次候选菜和过滤原因的强类型输入。 */
public class PreviewDishCandidatesInput {
    private Long customerId;
    private String customerCode;
    private Long orderId;
    private String orderCode;
    private String recordDate;
    private MealType mealType;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String value) { customerCode = value; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long value) { orderId = value; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String value) { orderCode = value; }
    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String value) { recordDate = value; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType value) { mealType = value; }
}
