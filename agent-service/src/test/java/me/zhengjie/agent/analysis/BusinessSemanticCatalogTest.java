package me.zhengjie.agent.analysis;

import me.zhengjie.agent.query.domain.AgentDefaultTemporalPolicy;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 指标知识目录必须唯一、版本化，并且不向模型暴露执行字段。 */
class BusinessSemanticCatalogTest {
    @Test
    void shouldProvideUniqueMetricsAndControlledTemporalPolicies() {
        BusinessSemanticCatalog catalog = new BusinessSemanticCatalog();
        Set<AgentQueryMetric> metrics = catalog.metrics(Set.of()).stream().map(item -> item.getMetric()).collect(Collectors.toSet());

        assertEquals(catalog.metrics(Set.of()).size(), metrics.size());
        assertEquals(AgentDefaultTemporalPolicy.CURRENT_DAY,
            AgentMetricCatalog.definition(AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT).getDefaultTemporalPolicy());
        assertEquals(AgentDefaultTemporalPolicy.NONE,
            AgentMetricCatalog.definition(AgentQueryMetric.ACTIVE_CUSTOMER_COUNT).getDefaultTemporalPolicy());
    }

    @Test
    void shouldRenderBusinessSemanticsWithoutToolOrResultField() {
        String prompt = new BusinessSemanticPromptRenderer(new BusinessSemanticCatalog()).render(new HashSet<>());

        assertTrue(prompt.contains("DAILY_UNSCHEDULED_CUSTOMER_COUNT"));
        assertFalse(prompt.contains("getDailyCustomerWorkload"));
        assertFalse(prompt.contains("unscheduledCustomerCount"));
    }
}
