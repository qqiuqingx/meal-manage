package me.zhengjie.agent.tool.input;

/** 查询排餐与菜品明细的强类型输入。 */
public class ListMealPlansInput {
    private Long customerId;
    private String customerCode;
    private Long orderId;
    private String orderCode;
    private String recordDate;
    private String startDate;
    private String endDate;
    private MealType mealType;
    private Integer page = 1;
    private Integer size = 50;

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
    public String getStartDate() { return startDate; }
    public void setStartDate(String value) { startDate = value; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String value) { endDate = value; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType value) { mealType = value; }
    public Integer getPage() { return page; }
    public void setPage(Integer value) { page = value; }
    public Integer getSize() { return size; }
    public void setSize(Integer value) { size = value; }
}
