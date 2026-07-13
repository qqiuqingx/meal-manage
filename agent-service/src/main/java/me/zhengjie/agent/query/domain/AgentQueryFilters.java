package me.zhengjie.agent.query.domain;

/**
 * Agent 查询支持的固定过滤条件；不支持字段名、排序表达式或 SQL 片段。
 */
public class AgentQueryFilters {

    private String recordDate;
    private String startDate;
    private String endDate;
    private String mealType;
    private String orderStatus;
    private Integer page;
    private Integer size;
    private Integer recentLimit;

    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String recordDate) { this.recordDate = recordDate; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public Integer getRecentLimit() { return recentLimit; }
    public void setRecentLimit(Integer recentLimit) { this.recentLimit = recentLimit; }
}
