package me.zhengjie.agent.query.domain;

import java.util.ArrayList;
import java.util.List;
import me.zhengjie.agent.analysis.domain.MealScope;

/**
 * 模型生成、服务端校验并执行的受控只读查询计划。
 */
public class AgentQueryPlan {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String SCHEMA_VERSION_V2 = "2.0";
    public static final String SCHEMA_VERSION_V3 = "3.0";

    private String version = SCHEMA_VERSION;
    private AgentQueryDomain domain;
    private AgentQueryAction action;
    private AgentEntityReference entities = new AgentEntityReference();
    private AgentQueryFilters filters = new AgentQueryFilters();
    private List<AgentQueryMetric> metrics = new ArrayList<>();
    private List<AgentQueryDimension> dimensions = new ArrayList<>();
    private AgentQuerySort sort;
    private Integer limit;
    private String metricVersion;
    private String timezone;
    private String analysisSource;
    private Double analysisConfidence;
    private String detailLevel = "SUMMARY";
    private MealScope mealScope;
    private List<String> toolNames = new ArrayList<>();
    /** V3 受控业务对象，仅允许服务端定义的语义枚举。 */
    private List<String> subjects = new ArrayList<>();
    /** V3 受控对象关系，不能表示 SQL Join 或工具名称。 */
    private List<String> relations = new ArrayList<>();
    /** V3 请求事实白名单。 */
    private List<String> requestedFacts = new ArrayList<>();
    /** V3 服务端可编译的聚合操作。 */
    private String operation;
    /** V3 服务端可编译的分组维度。 */
    private List<String> groupBy = new ArrayList<>();

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public AgentQueryDomain getDomain() { return domain; }
    public void setDomain(AgentQueryDomain domain) { this.domain = domain; }
    public AgentQueryAction getAction() { return action; }
    public void setAction(AgentQueryAction action) { this.action = action; }
    public AgentEntityReference getEntities() { return entities; }
    public void setEntities(AgentEntityReference entities) { this.entities = entities; }
    public AgentQueryFilters getFilters() { return filters; }
    public void setFilters(AgentQueryFilters filters) { this.filters = filters; }
    public List<AgentQueryMetric> getMetrics() { return metrics; }
    public void setMetrics(List<AgentQueryMetric> metrics) { this.metrics = metrics; }
    public List<AgentQueryDimension> getDimensions() { return dimensions; }
    public void setDimensions(List<AgentQueryDimension> dimensions) { this.dimensions = dimensions; }
    public AgentQuerySort getSort() { return sort; }
    public void setSort(AgentQuerySort sort) { this.sort = sort; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
    public String getMetricVersion() { return metricVersion; }
    public void setMetricVersion(String metricVersion) { this.metricVersion = metricVersion; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getAnalysisSource() { return analysisSource; }
    public void setAnalysisSource(String analysisSource) { this.analysisSource = analysisSource; }
    public Double getAnalysisConfidence() { return analysisConfidence; }
    public void setAnalysisConfidence(Double analysisConfidence) { this.analysisConfidence = analysisConfidence; }
    public String getDetailLevel() { return detailLevel; }
    public void setDetailLevel(String detailLevel) { this.detailLevel = detailLevel; }
    public MealScope getMealScope() { return mealScope; }
    public void setMealScope(MealScope mealScope) { this.mealScope = mealScope; }
    public List<String> getToolNames() { return toolNames; }
    public void setToolNames(List<String> toolNames) { this.toolNames = toolNames; }
    public List<String> getSubjects() { return subjects; }
    public void setSubjects(List<String> subjects) { this.subjects = subjects; }
    public List<String> getRelations() { return relations; }
    public void setRelations(List<String> relations) { this.relations = relations; }
    public List<String> getRequestedFacts() { return requestedFacts; }
    public void setRequestedFacts(List<String> requestedFacts) { this.requestedFacts = requestedFacts; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public List<String> getGroupBy() { return groupBy; }
    public void setGroupBy(List<String> groupBy) { this.groupBy = groupBy; }
}
