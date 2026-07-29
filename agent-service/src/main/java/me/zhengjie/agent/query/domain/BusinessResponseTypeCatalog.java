package me.zhengjie.agent.query.domain;

import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.tool.ToolCatalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 历史业务响应类型目录。
 *
 * <p>统一维护 responseType 对应的领域、动作、只读工具、上下文查询目标和事实展示元数据。
 * 兼容字符串只允许在该目录边界解析，Planner、Presenter 和会话摘要不再各自维护映射分支。</p>
 */
public final class BusinessResponseTypeCatalog {

    public static final String BUSINESS_QUERY = "BUSINESS_QUERY";
    public static final String CUSTOMER = "BUSINESS_QUERY_CUSTOMER";
    public static final String CUSTOMER_CANDIDATES = "BUSINESS_QUERY_CUSTOMER_CANDIDATES";
    public static final String ORDER = "BUSINESS_QUERY_ORDER";
    public static final String VERIFICATION = "BUSINESS_QUERY_VERIFICATION";
    public static final String REFUND = "BUSINESS_QUERY_REFUND";
    public static final String MEAL_PLAN = "BUSINESS_QUERY_MEAL_PLAN";
    public static final String PACKAGE = "BUSINESS_QUERY_PACKAGE";
    public static final String RULE = "BUSINESS_QUERY_RULE";
    public static final String DISH = "BUSINESS_QUERY_DISH";
    public static final String SCHEDULED_MENU = "BUSINESS_QUERY_SCHEDULED_MENU";
    public static final String DISH_CANDIDATES = "BUSINESS_QUERY_DISH_CANDIDATES";
    public static final String MEAL_PLAN_ALLERGY = "BUSINESS_QUERY_MEAL_PLAN_ALLERGY";
    public static final String ACTIVE_CUSTOMER_BALANCES =
        "BUSINESS_QUERY_ACTIVE_CUSTOMER_BALANCES";
    public static final String OPERATION_REPORT = "BUSINESS_QUERY_OPERATION_REPORT";

    private static final Map<String, Definition> DEFINITIONS = definitions();

    private BusinessResponseTypeCatalog() { }

    /**
     * 查找已登记响应类型。
     *
     * @param responseType 外部兼容响应类型
     * @return 强类型目录定义
     */
    public static Optional<Definition> find(String responseType) {
        return responseType == null ? Optional.empty()
            : Optional.ofNullable(DEFINITIONS.get(responseType));
    }

    /** 判断响应类型是否属于已登记的只读业务响应。 */
    public static boolean isBusinessResponse(String responseType) {
        return find(responseType).isPresent();
    }

    /** 返回不可变目录，供架构测试检查唯一性和工具登记关系。 */
    public static Map<String, Definition> definitionsView() {
        return DEFINITIONS;
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> result = new LinkedHashMap<>();
        register(result, new Definition(BUSINESS_QUERY, AgentQueryDomain.CUSTOMER,
            AgentQueryAction.OVERVIEW, List.of(ToolCatalog.CUSTOMER_OVERVIEW),
            BusinessQueryTarget.CUSTOMER, null));
        register(result, new Definition(CUSTOMER, AgentQueryDomain.CUSTOMER,
            AgentQueryAction.OVERVIEW, List.of(ToolCatalog.CUSTOMER_OVERVIEW),
            BusinessQueryTarget.CUSTOMER, null));
        register(result, new Definition(CUSTOMER_CANDIDATES, AgentQueryDomain.CUSTOMER,
            AgentQueryAction.LIST, List.of(ToolCatalog.RESOLVE_CUSTOMER),
            BusinessQueryTarget.CUSTOMER, new FactDefinition("候选客户数", "个",
            "CUSTOMER_CANDIDATE_LIST")));
        register(result, new Definition(ORDER, AgentQueryDomain.ORDER,
            AgentQueryAction.LIST, List.of(ToolCatalog.LIST_ORDERS),
            BusinessQueryTarget.ORDER, new FactDefinition("订单数量", "笔", "ORDER_LIST")));
        register(result, new Definition(VERIFICATION, AgentQueryDomain.VERIFICATION,
            AgentQueryAction.LIST, List.of(ToolCatalog.LIST_VERIFICATIONS),
            BusinessQueryTarget.VERIFICATION,
            new FactDefinition("核销记录数", "条", "VERIFICATION_LIST")));
        register(result, new Definition(REFUND, AgentQueryDomain.REFUND,
            AgentQueryAction.LIST, List.of(ToolCatalog.LIST_REFUNDS),
            BusinessQueryTarget.REFUND,
            new FactDefinition("退餐记录数", "条", "REFUND_LIST")));
        register(result, new Definition(MEAL_PLAN, AgentQueryDomain.MEAL_PLAN,
            AgentQueryAction.LIST, List.of(ToolCatalog.LIST_MEAL_PLANS),
            BusinessQueryTarget.CUSTOMER_MEAL_PLAN,
            new FactDefinition("排餐记录数", "条", "MEAL_PLAN_LIST")));
        register(result, new Definition(PACKAGE, AgentQueryDomain.PACKAGE,
            AgentQueryAction.DETAIL, List.of(ToolCatalog.PACKAGE_DETAIL),
            BusinessQueryTarget.PACKAGE, null));
        register(result, new Definition(RULE, AgentQueryDomain.BUSINESS_RULE,
            AgentQueryAction.EXPLAIN, List.of(ToolCatalog.EXPLAIN_RULE),
            BusinessQueryTarget.BUSINESS_RULE, null));
        register(result, new Definition(DISH, AgentQueryDomain.DISH,
            AgentQueryAction.LIST,
            List.of(ToolCatalog.LIST_MEAL_PLANS, ToolCatalog.LIST_DISHES),
            BusinessQueryTarget.CUSTOMER,
            new FactDefinition("菜品数量", "道", "DISH_LIST")));
        register(result, new Definition(SCHEDULED_MENU, AgentQueryDomain.DISH,
            AgentQueryAction.LIST, List.of(ToolCatalog.LIST_SCHEDULED_DISHES),
            BusinessQueryTarget.SCHEDULED_MENU,
            new FactDefinition("排期菜品数", "道", "SCHEDULED_DISH_LIST")));
        register(result, new Definition(DISH_CANDIDATES, AgentQueryDomain.DISH,
            AgentQueryAction.LIST, List.of(ToolCatalog.PREVIEW_DISH_CANDIDATES),
            BusinessQueryTarget.DISH_CANDIDATES, null));
        register(result, new Definition(MEAL_PLAN_ALLERGY, AgentQueryDomain.MEAL_PLAN,
            AgentQueryAction.LIST, List.of(ToolCatalog.LIST_MEAL_PLANS),
            BusinessQueryTarget.MEAL_PLAN_ALLERGY_ANALYSIS, null));
        register(result, new Definition(OPERATION_REPORT,
            AgentQueryDomain.NATURAL_LANGUAGE_REPORT, AgentQueryAction.SUMMARY,
            List.of(ToolCatalog.GET_DAILY_CUSTOMER_WORKLOAD),
            BusinessQueryTarget.OPERATION_STATISTICS, null));

        for (AgentMetricDefinition metric : AgentMetricCatalog.definitionsView()) {
            String responseType = metric.getResponseType();
            if (result.containsKey(responseType)) continue;
            AgentQueryAction action =
                metric.getMetric() == AgentQueryMetric.ACTIVE_CUSTOMER_MEAL_BALANCE_DETAIL
                    ? AgentQueryAction.BREAKDOWN : AgentQueryAction.SUMMARY;
            register(result, new Definition(responseType, metric.getDomain(), action,
                List.of(metric.getToolName()), BusinessQueryTarget.OPERATION_STATISTICS,
                new FactDefinition(metric.getDisplayName(), metric.getResultUnit(),
                    metric.getMetricVersion()), metric.getMetric()));
        }
        // 历史 DAILY 名称保持兼容，统一指向已登记的待核销指标。
        register(result, new Definition("BUSINESS_QUERY_OPERATION_DAILY",
            AgentQueryDomain.OPERATION_STATISTICS, AgentQueryAction.SUMMARY,
            List.of(ToolCatalog.GET_DAILY_CUSTOMER_WORKLOAD),
            BusinessQueryTarget.OPERATION_STATISTICS,
            new FactDefinition("待核销客户数", "客户", AgentMetricCatalog.VERSION),
            AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT));
        return Map.copyOf(result);
    }

    private static void register(Map<String, Definition> target, Definition definition) {
        if (target.putIfAbsent(definition.responseType(), definition) != null) {
            throw new IllegalStateException(
                "Duplicate business response type: " + definition.responseType());
        }
    }

    /** 一个响应类型的强类型规划和展示元数据。 */
    public record Definition(String responseType, AgentQueryDomain domain,
                             AgentQueryAction action, List<String> toolNames,
                             BusinessQueryTarget queryTarget, FactDefinition totalFact,
                             AgentQueryMetric metric) {
        public Definition(String responseType, AgentQueryDomain domain,
                          AgentQueryAction action, List<String> toolNames,
                          BusinessQueryTarget queryTarget, FactDefinition totalFact) {
            this(responseType, domain, action, List.copyOf(toolNames),
                queryTarget, totalFact, null);
        }

        /** 防止调用方修改目录中的工具集合。 */
        public Definition {
            toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
        }
    }

    /** total 事实的固定展示定义。 */
    public record FactDefinition(String label, String unit, String sourceType) { }
}
