package me.zhengjie.agent.query.domain;

import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.MealScope;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 可持久化的上一轮业务查询脱敏摘要，仅供纠错重新规划使用。 */
public class LastBusinessQueryContext {
    private String responseType;
    private BusinessQueryTarget queryTarget;
    private String queryPlanFingerprint;
    private String domain;
    private String recordDate;
    private String startDate;
    private String endDate;
    private String metric;
    private MealScope mealScope;
    private Map<String, Object> resultShape = new LinkedHashMap<>();
    private String assistantSummary;
    private OffsetDateTime queriedAt;

    public String getResponseType() { return responseType; }
    public void setResponseType(String responseType) { this.responseType = responseType; }
    public BusinessQueryTarget getQueryTarget() { return queryTarget; }
    public void setQueryTarget(BusinessQueryTarget queryTarget) { this.queryTarget = queryTarget; }
    public String getQueryPlanFingerprint() { return queryPlanFingerprint; }
    public void setQueryPlanFingerprint(String queryPlanFingerprint) { this.queryPlanFingerprint = queryPlanFingerprint; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String recordDate) { this.recordDate = recordDate; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public MealScope getMealScope() { return mealScope; }
    public void setMealScope(MealScope mealScope) { this.mealScope = mealScope; }
    public Map<String, Object> getResultShape() { return resultShape; }
    public void setResultShape(Map<String, Object> resultShape) { this.resultShape = resultShape; }
    public String getAssistantSummary() { return assistantSummary; }
    public void setAssistantSummary(String assistantSummary) { this.assistantSummary = assistantSummary; }
    public OffsetDateTime getQueriedAt() { return queriedAt; }
    public void setQueriedAt(OffsetDateTime queriedAt) { this.queriedAt = queriedAt; }
}
