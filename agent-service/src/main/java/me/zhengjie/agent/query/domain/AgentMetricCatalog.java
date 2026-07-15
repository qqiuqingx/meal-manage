package me.zhengjie.agent.query.domain;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Agent 可查询指标白名单及版本化口径。模型只接收业务语义字段，工具和结果字段仅供服务端编译。
 */
public final class AgentMetricCatalog {

    public static final String VERSION = "2026.07";
    private static final Map<AgentQueryMetric, AgentMetricDefinition> DEFINITIONS = definitions();

    private AgentMetricCatalog() { }

    /** 返回已登记指标的定义；未登记指标返回 null。 */
    public static AgentMetricDefinition definition(AgentQueryMetric metric) {
        return metric == null ? null : DEFINITIONS.get(metric);
    }

    /** 返回不可变的完整指标定义集合，用于目录校验和受控 Prompt 渲染。 */
    public static Collection<AgentMetricDefinition> definitionsView() {
        return DEFINITIONS.values();
    }

    /** 按服务端固定响应类型查找指标定义；该映射不读取用户文本。 */
    public static AgentMetricDefinition definitionByResponseType(String responseType) {
        if (responseType == null) return null;
        return DEFINITIONS.values().stream()
            .filter(definition -> responseType.equals(definition.getResponseType()))
            .findFirst().orElse(null);
    }

    private static Map<AgentQueryMetric, AgentMetricDefinition> definitions() {
        Map<AgentQueryMetric, AgentMetricDefinition> result = new EnumMap<>(AgentQueryMetric.class);
        register(result, AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT, "已排餐客户数",
            "指定业务日已生成有效排餐的客户去重数", AgentQueryDomain.OPERATION_STATISTICS,
            AgentDefaultTemporalPolicy.CURRENT_DAY, true, "客户", "scheduledCustomerCount",
            List.of("已排餐客户", "已经安排餐"), "getDailyCustomerWorkload", "BUSINESS_QUERY_OPERATION_SCHEDULED",
            AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT, "已核销客户数",
            "指定业务日已完成有效核销的客户去重数", AgentQueryDomain.OPERATION_STATISTICS,
            AgentDefaultTemporalPolicy.CURRENT_DAY, true, "客户", "verifiedCustomerCount",
            List.of("已核销客户", "已经核销"), "getDailyCustomerWorkload", "BUSINESS_QUERY_OPERATION_VERIFIED",
            AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT, "待核销客户数",
            "指定业务日已排餐但尚未核销的客户去重数", AgentQueryDomain.OPERATION_STATISTICS,
            AgentDefaultTemporalPolicy.CURRENT_DAY, true, "客户", "unverifiedCustomerCount",
            List.of("排了但没核销", "待核销客户"), "getDailyCustomerWorkload", "BUSINESS_QUERY_OPERATION_UNVERIFIED",
            AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.DAILY_EXPECTED_CUSTOMER_COUNT, "应服务客户数",
            "指定业务日按订单有效期、餐次、排餐模式、排除规则和餐数池上限应服务的客户数", AgentQueryDomain.OPERATION_STATISTICS,
            AgentDefaultTemporalPolicy.CURRENT_DAY, true, "客户", "expectedCustomerCount",
            List.of("应该送餐", "应服务客户"), "getDailyCustomerWorkload", "BUSINESS_QUERY_OPERATION_EXPECTED",
            AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT, "待排餐客户数",
            "指定业务日应服务但尚未生成有效排餐的客户数，不等同于所有仍有餐数的客户", AgentQueryDomain.OPERATION_STATISTICS,
            AgentDefaultTemporalPolicy.CURRENT_DAY, true, "客户", "unscheduledCustomerCount",
            List.of("有餐数没有排餐", "待排餐客户", "还没安排餐"), "getDailyCustomerWorkload", "BUSINESS_QUERY_OPERATION_UNSCHEDULED",
            AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.CUSTOMER_PROFILE_COUNT, "客户档案总数",
            "当前授权数据范围内已录入系统的客户档案数量", AgentQueryDomain.OPERATION_STATISTICS,
            AgentDefaultTemporalPolicy.NONE, false, "位", "total",
            List.of("系统客户总数", "客户档案总数", "录入客户数"), "getCustomerProfileCount", "BUSINESS_QUERY_OPERATION_CUSTOMER_TOTAL");
        register(result, AgentQueryMetric.ACTIVE_CUSTOMER_COUNT, "活跃客户数",
            "存在进行中且按订单餐数与有效核销实时计算后仍有任一餐数池余额的客户去重数，不要求当天未排餐", AgentQueryDomain.OPERATION_STATISTICS,
            AgentDefaultTemporalPolicy.NONE, false, "客户", "total",
            List.of("还有餐数的客户", "活跃客户"), "getActiveCustomerSummary", "BUSINESS_QUERY_OPERATION_ACTIVE",
            new AgentQueryDimension[0]);
        register(result, AgentQueryMetric.ACTIVE_ORDER_COUNT, "进行中订单数", "状态为进行中且未删除的订单数",
            AgentQueryDomain.OPERATION_STATISTICS, AgentDefaultTemporalPolicy.NONE, false, "订单", "total",
            List.of("进行中订单"), "getActiveCustomerSummary", "BUSINESS_QUERY_OPERATION_ACTIVE_ORDER",
            AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE, AgentQueryDimension.ORDER_STATUS);
        register(result, AgentQueryMetric.EXPIRING_ORDER_COUNT, "即将到期订单数", "指定日期范围内到期的进行中订单数",
            AgentQueryDomain.OPERATION_STATISTICS, AgentDefaultTemporalPolicy.REQUIRE_EXPLICIT, false, "订单", "total",
            List.of("即将到期", "快到期订单"), "getExpiringOrderSummary", "BUSINESS_QUERY_OPERATION_EXPIRING",
            AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
        register(result, AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT, "排餐失败数", "指定业务日、可选餐次的排餐失败记录数",
            AgentQueryDomain.OPERATION_STATISTICS, AgentDefaultTemporalPolicy.CURRENT_DAY, true, "记录", "mealPlanFailureCount",
            List.of("排餐失败", "生成失败"), "getMealPlanFailureSummary", "BUSINESS_QUERY_OPERATION_FAILURE",
            AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE);
        register(result, AgentQueryMetric.VERIFICATION_COUNT, "核销次数", "指定日期或日期范围内未删除的有效核销次数",
            AgentQueryDomain.VERIFICATION, AgentDefaultTemporalPolicy.REQUIRE_EXPLICIT, false, "餐次", "total",
            List.of("核销次数", "核销了多少餐"), "listVerifications", "BUSINESS_QUERY_VERIFICATION",
            AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.VERIFICATION_STATUS);
        register(result, AgentQueryMetric.REFUND_COUNT, "退餐次数", "指定日期或日期范围内的退餐记录数",
            AgentQueryDomain.REFUND, AgentDefaultTemporalPolicy.REQUIRE_EXPLICIT, false, "记录", "total",
            List.of("退餐次数"), "listRefunds", "BUSINESS_QUERY_REFUND",
            AgentQueryDimension.RECORD_DATE, AgentQueryDimension.MEAL_TYPE);
        return Map.copyOf(result);
    }

    private static void register(Map<AgentQueryMetric, AgentMetricDefinition> target, AgentQueryMetric metric,
                                 String displayName, String semanticDescription, AgentQueryDomain domain,
                                 AgentDefaultTemporalPolicy temporalPolicy, boolean requiresSingleDate,
                                 String resultUnit, String resultFieldKey, List<String> terms,
                                 String toolName, String responseType, AgentQueryDimension... dimensions) {
        target.put(metric, new AgentMetricDefinition(metric, displayName, semanticDescription, domain,
            semanticDescription, temporalPolicy, requiresSingleDate, resultUnit, resultFieldKey, terms,
            toolName, responseType, VERSION, 31,
            dimensions.length == 0 ? EnumSet.noneOf(AgentQueryDimension.class) : EnumSet.of(dimensions[0], dimensions)));
    }
}
