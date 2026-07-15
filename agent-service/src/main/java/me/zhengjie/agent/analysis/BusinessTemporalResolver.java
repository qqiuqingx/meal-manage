package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.analysis.domain.BusinessTemporalExpression;
import me.zhengjie.agent.analysis.domain.BusinessTemporalIntent;
import me.zhengjie.agent.config.BusinessTimeProperties;
import me.zhengjie.agent.query.domain.AgentDefaultTemporalPolicy;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * 将受控相对时间按固定业务时区落为 QueryPlan 可执行日期。该服务不读取自然语言，也不依赖机器当前时区。
 */
public class BusinessTemporalResolver {
    private final Clock clock;
    private final ZoneId zoneId;
    private final BusinessTemporalExpression defaultDailyExpression;

    public BusinessTemporalResolver(Clock clock, BusinessTimeProperties properties) {
        this.clock = clock;
        this.zoneId = properties.toZoneId();
        BusinessTemporalExpression configured = properties.getDefaultDailyExpression();
        this.defaultDailyExpression = configured == BusinessTemporalExpression.PREVIOUS_DAY
            || configured == BusinessTemporalExpression.NEXT_DAY ? configured : BusinessTemporalExpression.CURRENT_DAY;
    }

    /**
     * 按“明确过滤条件、本轮语义、Pending、最近查询、指标默认策略”优先级解析日期并回写分析结果。
     *
     * @param analysis 已通过结构校验的业务语义
     * @param pendingContext 待补条件查询，可为空
     * @param lastContext 最近一次已执行查询，可为空
     * @return 原分析对象；无法满足显式时间要求时标记为需要澄清
     */
    public BusinessQuestionAnalysis resolve(BusinessQuestionAnalysis analysis,
                                            PendingBusinessQueryContext pendingContext,
                                            LastBusinessQueryContext lastContext) {
        if (analysis == null) return null;
        AgentQueryFilters filters = analysis.getFilters() == null ? new AgentQueryFilters() : analysis.getFilters();
        analysis.setFilters(filters);
        normalizeExistingDates(filters, analysis);
        if (hasDate(filters)) return analysis;

        BusinessTemporalIntent temporal = analysis.getTemporal();
        BusinessTemporalExpression expression = temporal == null || temporal.getExpression() == null
            ? BusinessTemporalExpression.UNSPECIFIED : temporal.getExpression();
        AgentMetricDefinition definition = primaryDefinition(analysis);
        AgentDefaultTemporalPolicy policy = definition == null ? AgentDefaultTemporalPolicy.NONE : definition.getDefaultTemporalPolicy();
        if (policy == AgentDefaultTemporalPolicy.NONE && expression == BusinessTemporalExpression.CURRENT_DAY) {
            temporal.setExpression(BusinessTemporalExpression.UNSPECIFIED);
            return analysis;
        }
        if (expression != BusinessTemporalExpression.UNSPECIFIED) {
            if (!applyExpression(filters, temporal, expression, lastContext)) invalidTime(analysis);
            return analysis;
        }
        if (pendingContext != null && pendingContext.getAnalysis() != null && pendingContext.getAnalysis().getFilters() != null
            && copyDateFilters(pendingContext.getAnalysis().getFilters(), filters)) return analysis;

        if (policy == AgentDefaultTemporalPolicy.INHERIT_OR_CURRENT && copyLastDate(lastContext, filters)) return analysis;
        if (policy == AgentDefaultTemporalPolicy.CURRENT_DAY || policy == AgentDefaultTemporalPolicy.INHERIT_OR_CURRENT) {
            BusinessTemporalIntent configuredTemporal = new BusinessTemporalIntent();
            configuredTemporal.setExpression(defaultDailyExpression);
            if (!applyExpression(filters, configuredTemporal, defaultDailyExpression, lastContext)) {
                invalidTime(analysis);
                return analysis;
            }
            setResolvedExpression(analysis, defaultDailyExpression);
        } else if (policy == AgentDefaultTemporalPolicy.REQUIRE_EXPLICIT) {
            analysis.setRequiresClarification(true);
            analysis.setClarificationQuestion("请补充查询日期或日期范围。");
        }
        return analysis;
    }

    /** 返回业务时区，供 QueryPlan 和语义追踪使用。 */
    public ZoneId getZoneId() { return zoneId; }

    private void normalizeExistingDates(AgentQueryFilters filters, BusinessQuestionAnalysis analysis) {
        try {
            if (notBlank(filters.getRecordDate())) LocalDate.parse(filters.getRecordDate());
            if (notBlank(filters.getStartDate()) || notBlank(filters.getEndDate())) {
                LocalDate start = LocalDate.parse(filters.getStartDate());
                LocalDate end = LocalDate.parse(filters.getEndDate());
                if (start.isAfter(end)) throw new IllegalArgumentException("start after end");
            }
        } catch (RuntimeException exception) {
            filters.setRecordDate(null); filters.setStartDate(null); filters.setEndDate(null);
            invalidTime(analysis);
        }
    }

    private boolean applyExpression(AgentQueryFilters filters, BusinessTemporalIntent temporal,
                                    BusinessTemporalExpression expression, LastBusinessQueryContext lastContext) {
        try {
            LocalDate today = today();
            switch (expression) {
                case CURRENT_DAY: filters.setRecordDate(today.toString()); return noExplicitDates(temporal);
                case PREVIOUS_DAY: filters.setRecordDate(today.minusDays(1).toString()); return noExplicitDates(temporal);
                case NEXT_DAY: filters.setRecordDate(today.plusDays(1).toString()); return noExplicitDates(temporal);
                case CURRENT_WEEK:
                    filters.setStartDate(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());
                    filters.setEndDate(today.toString());
                    return noExplicitDates(temporal);
                case EXPLICIT_DATE:
                    filters.setRecordDate(LocalDate.parse(temporal.getExplicitDate()).toString()); return true;
                case EXPLICIT_RANGE:
                    LocalDate start = LocalDate.parse(temporal.getExplicitStartDate());
                    LocalDate end = LocalDate.parse(temporal.getExplicitEndDate());
                    if (start.isAfter(end)) return false;
                    filters.setStartDate(start.toString()); filters.setEndDate(end.toString()); return true;
                case INHERIT_PREVIOUS: return copyLastDate(lastContext, filters);
                default: return true;
            }
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean copyLastDate(LastBusinessQueryContext context, AgentQueryFilters filters) {
        if (context == null) return false;
        if (notBlank(context.getRecordDate())) { filters.setRecordDate(context.getRecordDate()); return true; }
        if (notBlank(context.getStartDate()) && notBlank(context.getEndDate())) {
            filters.setStartDate(context.getStartDate()); filters.setEndDate(context.getEndDate()); return true;
        }
        return false;
    }

    private boolean copyDateFilters(AgentQueryFilters source, AgentQueryFilters target) {
        if (notBlank(source.getRecordDate())) { target.setRecordDate(source.getRecordDate()); return true; }
        if (notBlank(source.getStartDate()) && notBlank(source.getEndDate())) {
            target.setStartDate(source.getStartDate()); target.setEndDate(source.getEndDate()); return true;
        }
        return false;
    }

    private AgentMetricDefinition primaryDefinition(BusinessQuestionAnalysis analysis) {
        if (analysis.getMetrics() == null || analysis.getMetrics().isEmpty()) return null;
        AgentQueryMetric metric = analysis.getMetrics().get(0);
        return AgentMetricCatalog.definition(metric);
    }

    private void invalidTime(BusinessQuestionAnalysis analysis) {
        analysis.setRequiresClarification(true);
        analysis.setClarificationQuestion("时间条件无效，请提供今天、明天或 yyyy-MM-dd 格式的日期。");
    }

    private void setResolvedExpression(BusinessQuestionAnalysis analysis, BusinessTemporalExpression expression) {
        BusinessTemporalIntent temporal = analysis.getTemporal() == null ? new BusinessTemporalIntent() : analysis.getTemporal();
        temporal.setExpression(expression); analysis.setTemporal(temporal);
    }

    private boolean noExplicitDates(BusinessTemporalIntent temporal) {
        return !notBlank(temporal.getExplicitDate()) && !notBlank(temporal.getExplicitStartDate()) && !notBlank(temporal.getExplicitEndDate());
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(zoneId)); }
    private boolean hasDate(AgentQueryFilters filters) { return notBlank(filters.getRecordDate()) || notBlank(filters.getStartDate()) && notBlank(filters.getEndDate()); }
    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
}
