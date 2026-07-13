package me.zhengjie.agent.query.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 单个指标的业务口径、支持维度和日期边界定义。
 */
public final class AgentMetricDefinition {

    private final AgentQueryMetric metric;
    private final String displayName;
    private final String definition;
    private final String metricVersion;
    private final int maxDateRangeDays;
    private final Set<AgentQueryDimension> dimensions;

    public AgentMetricDefinition(AgentQueryMetric metric, String displayName, String definition,
                                 String metricVersion, int maxDateRangeDays,
                                 Set<AgentQueryDimension> dimensions) {
        this.metric = metric;
        this.displayName = displayName;
        this.definition = definition;
        this.metricVersion = metricVersion;
        this.maxDateRangeDays = maxDateRangeDays;
        this.dimensions = dimensions == null || dimensions.isEmpty()
            ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(dimensions));
    }

    public AgentQueryMetric getMetric() { return metric; }
    public String getDisplayName() { return displayName; }
    public String getDefinition() { return definition; }
    public String getMetricVersion() { return metricVersion; }
    public int getMaxDateRangeDays() { return maxDateRangeDays; }
    public Set<AgentQueryDimension> getDimensions() { return dimensions; }
}
