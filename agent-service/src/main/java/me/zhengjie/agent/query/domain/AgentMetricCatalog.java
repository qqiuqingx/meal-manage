package me.zhengjie.agent.query.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Agent 可查询指标白名单及版本化口径。金额和自由字段不得登记到本目录。
 */
public final class AgentMetricCatalog {

    public static final String VERSION = "2026.07";
    private static final Map<AgentQueryMetric, AgentMetricDefinition> DEFINITIONS = definitions();

    private AgentMetricCatalog() { }

    /** 返回已登记指标的定义；未登记指标返回 null。 */
    public static AgentMetricDefinition definition(AgentQueryMetric metric) {
        return metric == null ? null : DEFINITIONS.get(metric);
    }

    private static Map<AgentQueryMetric, AgentMetricDefinition> definitions() {
        Map<AgentQueryMetric, AgentMetricDefinition> result = new EnumMap<>(AgentQueryMetric.class);
        register(result, AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT, "当日已排餐客户数", "指定日期有效排餐的客户去重数", 31, AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT, "当日已核销客户数", "指定日期已核销排餐的客户去重数", 31, AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT, "当日待核销客户数", "指定日期已排餐但未核销的客户去重数", 31, AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.DAILY_EXPECTED_CUSTOMER_COUNT, "当日应服务客户数", "按有效订单、排餐模式及排除规则计算的客户加餐次去重数", 31, AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT, "当日待排餐客户数", "应服务客户减去已生成有效排餐客户，按客户和餐次去重", 31, AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.ACTIVE_CUSTOMER_COUNT, "活跃客户数", "存在进行中且仍有剩余餐数订单的客户去重数", 31, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.ACTIVE_ORDER_COUNT, "进行中订单数", "状态为进行中且未删除订单数", 31, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE, AgentQueryDimension.ORDER_STATUS);
        register(result, AgentQueryMetric.EXPIRING_ORDER_COUNT, "即将到期订单数", "指定日期范围内到期的进行中订单数", 31, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT, "排餐失败数", "指定日期和餐次生成失败的客户排餐记录数", 31, AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE);
        register(result, AgentQueryMetric.VERIFICATION_COUNT, "核销次数", "未删除核销记录的受控统计", 31, AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.VERIFICATION_STATUS);
        register(result, AgentQueryMetric.REFUND_COUNT, "退餐次数", "未删除退餐记录的受控统计", 31, AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE);
        return Map.copyOf(result);
    }

    private static void register(Map<AgentQueryMetric, AgentMetricDefinition> target, AgentQueryMetric metric,
                                 String displayName, String definition, int maxDays, AgentQueryDimension... dimensions) {
        target.put(metric, new AgentMetricDefinition(metric, displayName, definition, VERSION, maxDays,
            dimensions.length == 0 ? EnumSet.noneOf(AgentQueryDimension.class) : EnumSet.of(dimensions[0], dimensions)));
    }
}
