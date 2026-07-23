package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.analysis.domain.BusinessTemporalExpression;
import me.zhengjie.agent.analysis.domain.BusinessTemporalIntent;
import me.zhengjie.agent.config.BusinessTimeProperties;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 相对时间必须按固定业务时区解析，测试不得依赖机器当前日期。 */
class BusinessTemporalResolverTest {
    private final BusinessTemporalResolver resolver = resolver();

    @Test
    void shouldResolveCurrentPreviousNextAndCurrentWeek() {
        assertEquals("2026-07-14", resolve(BusinessTemporalExpression.CURRENT_DAY).getFilters().getRecordDate());
        assertEquals("2026-07-13", resolve(BusinessTemporalExpression.PREVIOUS_DAY).getFilters().getRecordDate());
        assertEquals("2026-07-15", resolve(BusinessTemporalExpression.NEXT_DAY).getFilters().getRecordDate());
        BusinessQuestionAnalysis week = resolve(BusinessTemporalExpression.CURRENT_WEEK);
        assertEquals("2026-07-13", week.getFilters().getStartDate());
        assertEquals("2026-07-14", week.getFilters().getEndDate());
    }

    @Test
    void shouldApplyCurrentDayDefaultToDailyUnscheduledMetric() {
        BusinessQuestionAnalysis analysis = operation(AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT);

        resolver.resolve(analysis, null, null);

        assertEquals("2026-07-14", analysis.getFilters().getRecordDate());
        assertEquals(BusinessTemporalExpression.CURRENT_DAY, analysis.getTemporal().getExpression());
    }

    @Test
    void shouldApplyConfiguredDailyDefaultExpression() {
        BusinessTimeProperties properties = new BusinessTimeProperties();
        properties.setZoneId("Asia/Shanghai");
        properties.setDefaultDailyExpression(BusinessTemporalExpression.NEXT_DAY);
        BusinessTemporalResolver configured = new BusinessTemporalResolver(
            Clock.fixed(Instant.parse("2026-07-14T04:00:00Z"), ZoneId.of("UTC")), properties);
        BusinessQuestionAnalysis analysis = operation(AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT);

        configured.resolve(analysis, null, null);

        assertEquals("2026-07-15", analysis.getFilters().getRecordDate());
        assertEquals(BusinessTemporalExpression.NEXT_DAY, analysis.getTemporal().getExpression());
    }

    @Test
    void shouldRequireExplicitTimeForVerificationMetric() {
        BusinessQuestionAnalysis analysis = operation(AgentQueryMetric.VERIFICATION_COUNT);

        resolver.resolve(analysis, null, null);

        assertTrue(analysis.isRequiresClarification());
    }

    /** 无日期指标的明细追问只能继承客户集合，不能强制上一轮提供不存在的日期。 */
    @Test
    void shouldIgnoreInheritedTimeForDateIndependentMetric() {
        BusinessQuestionAnalysis analysis = operation(AgentQueryMetric.ACTIVE_CUSTOMER_MEAL_BALANCE_DETAIL);
        BusinessTemporalIntent temporal = new BusinessTemporalIntent();
        temporal.setExpression(BusinessTemporalExpression.INHERIT_PREVIOUS);
        analysis.setTemporal(temporal);

        resolver.resolve(analysis, null, new LastBusinessQueryContext());

        assertFalse(analysis.isRequiresClarification());
        assertEquals(BusinessTemporalExpression.UNSPECIFIED, analysis.getTemporal().getExpression());
        assertNull(analysis.getFilters().getRecordDate());
    }

    private BusinessQuestionAnalysis resolve(BusinessTemporalExpression expression) {
        BusinessQuestionAnalysis analysis = operation(AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT);
        BusinessTemporalIntent temporal = new BusinessTemporalIntent(); temporal.setExpression(expression);
        analysis.setTemporal(temporal);
        return resolver.resolve(analysis, null, null);
    }

    private BusinessQuestionAnalysis operation(AgentQueryMetric metric) {
        BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
        analysis.setDomains(List.of(metric == AgentQueryMetric.VERIFICATION_COUNT
            ? AgentQueryDomain.VERIFICATION : AgentQueryDomain.OPERATION_STATISTICS));
        analysis.setMetrics(List.of(metric));
        return analysis;
    }

    private BusinessTemporalResolver resolver() {
        BusinessTimeProperties properties = new BusinessTimeProperties(); properties.setZoneId("Asia/Shanghai");
        return new BusinessTemporalResolver(Clock.fixed(Instant.parse("2026-07-14T04:00:00Z"), ZoneId.of("UTC")), properties);
    }
}
