package me.zhengjie.agent.tool.input;

import java.util.ArrayList;
import java.util.List;

/** 查询登记运营指标的强类型输入。 */
public class QueryBusinessMetricsInput {
    private MetricName metric;
    private String startDate;
    private String endDate;
    private String recordDate;
    private MealType mealType;
    private List<MetricDimension> dimensions = new ArrayList<>();

    public MetricName getMetric() { return metric; }
    public void setMetric(MetricName value) { metric = value; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String value) { startDate = value; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String value) { endDate = value; }
    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String value) { recordDate = value; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType value) { mealType = value; }
    public List<MetricDimension> getDimensions() { return dimensions; }
    public void setDimensions(List<MetricDimension> value) { dimensions = value == null ? new ArrayList<>() : value; }
}
