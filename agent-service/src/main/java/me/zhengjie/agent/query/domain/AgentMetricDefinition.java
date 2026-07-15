package me.zhengjie.agent.query.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;

/**
 * 单个指标的业务口径、支持维度和日期边界定义。
 */
public final class AgentMetricDefinition {

    private final AgentQueryMetric metric;
    private final String displayName;
    private final String definition;
    private final AgentQueryDomain domain;
    private final String semanticDescription;
    private final AgentDefaultTemporalPolicy defaultTemporalPolicy;
    private final boolean requiresSingleDate;
    private final String resultUnit;
    private final String resultFieldKey;
    private final List<String> commonBusinessTerms;
    private final String toolName;
    private final String responseType;
    private final String metricVersion;
    private final int maxDateRangeDays;
    private final Set<AgentQueryDimension> dimensions;

    public AgentMetricDefinition(AgentQueryMetric metric, String displayName, String definition,
                                 AgentQueryDomain domain, String semanticDescription,
                                 AgentDefaultTemporalPolicy defaultTemporalPolicy, boolean requiresSingleDate,
                                 String resultUnit, String resultFieldKey, List<String> commonBusinessTerms,
                                 String toolName, String responseType, String metricVersion,
                                 int maxDateRangeDays, Set<AgentQueryDimension> dimensions) {
        this.metric = metric;
        this.displayName = displayName;
        this.definition = definition;
        this.domain = domain;
        this.semanticDescription = semanticDescription;
        this.defaultTemporalPolicy = defaultTemporalPolicy;
        this.requiresSingleDate = requiresSingleDate;
        this.resultUnit = resultUnit;
        this.resultFieldKey = resultFieldKey;
        this.commonBusinessTerms = commonBusinessTerms == null ? List.of() : List.copyOf(commonBusinessTerms);
        this.toolName = toolName;
        this.responseType = responseType;
        this.metricVersion = metricVersion;
        this.maxDateRangeDays = maxDateRangeDays;
        this.dimensions = dimensions == null || dimensions.isEmpty()
            ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(dimensions));
    }

    public AgentQueryMetric getMetric() { return metric; }
    public String getDisplayName() { return displayName; }
    public String getDefinition() { return definition; }
    public AgentQueryDomain getDomain() { return domain; }
    public String getSemanticDescription() { return semanticDescription; }
    public AgentDefaultTemporalPolicy getDefaultTemporalPolicy() { return defaultTemporalPolicy; }
    public boolean isRequiresSingleDate() { return requiresSingleDate; }
    public String getResultUnit() { return resultUnit; }
    public String getResultFieldKey() { return resultFieldKey; }
    public List<String> getCommonBusinessTerms() { return commonBusinessTerms; }
    public String getToolName() { return toolName; }
    public String getResponseType() { return responseType; }
    public String getMetricVersion() { return metricVersion; }
    public int getMaxDateRangeDays() { return maxDateRangeDays; }
    public Set<AgentQueryDimension> getDimensions() { return dimensions; }
}
