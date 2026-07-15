package me.zhengjie.agent.query.tool;

import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用业务只读工具注册表；仅登记固定接口，禁止模型构造任意 URL、SQL 或工具名。
 */
public final class AgentBusinessToolRegistry {
    private static final Map<String, AgentBusinessToolDescriptor> TOOLS = tools();
    private AgentBusinessToolRegistry() { }

    /** 判断工具名是否已登记。 */
    public static boolean isRegistered(String name) { return name != null && TOOLS.containsKey(name); }
    /** 返回受控工具描述集合。 */
    public static Collection<AgentBusinessToolDescriptor> descriptors() { return TOOLS.values(); }
    /** 返回单个已登记工具的受控描述；未登记时返回 null。 */
    public static AgentBusinessToolDescriptor descriptor(String name) { return name == null ? null : TOOLS.get(name); }

    /**
     * 判断工具是否在主系统计算的本轮可用集合中；未下发白名单的旧调用保持兼容。
     *
     * @param name 工具名
     * @param availableTools 本轮可用工具集合，null 表示旧调用
     * @return 工具可执行时为 true
     */
    public static boolean isAvailable(String name, java.util.Set<String> availableTools) {
        return isRegistered(name) && (availableTools == null || availableTools.contains(name));
    }

    private static Map<String, AgentBusinessToolDescriptor> tools() {
        Map<String, AgentBusinessToolDescriptor> result = new LinkedHashMap<>();
        register(result, "resolveCustomer", AgentQueryDomain.CUSTOMER, AgentQueryAction.LIST, "customerProfile:list", 10, "customerId|customerCode|customerName", "AgentCustomerCandidateDto[]");
        register(result, "customerOverview", AgentQueryDomain.CUSTOMER, AgentQueryAction.OVERVIEW, "customerProfile:list", 1, "customerId|customerCode", "AgentCustomerOverviewDto");
        register(result, "listOrders", AgentQueryDomain.ORDER, AgentQueryAction.LIST, "customerOrder:list", 20, "customerId|status|page|size", "AgentOrderSummaryDto[]");
        register(result, "orderDetail", AgentQueryDomain.ORDER, AgentQueryAction.DETAIL, "customerOrder:list", 1, "orderId|orderCode|customerId", "AgentOrderSummaryDto");
        register(result, "listMealPlans", AgentQueryDomain.MEAL_PLAN, AgentQueryAction.LIST, "mealPlan:list", 31, "customerId|recordDate|mealType", "AgentMealPlanSummaryDto[]");
        register(result, "listVerifications", AgentQueryDomain.VERIFICATION, AgentQueryAction.LIST, "mealPlan:list", 50, "customerId|orderId|mealType|limit", "AgentVerificationLogDto[]");
        register(result, "listRefunds", AgentQueryDomain.REFUND, AgentQueryAction.LIST, "customerOrder:list+mealPlan:list", 50, "customerId|orderId|limit", "AgentRefundLogDto[]");
        register(result, "packageDetail", AgentQueryDomain.PACKAGE, AgentQueryAction.DETAIL, "package:list", 5, "parentPackageId", "AgentPackageSpecDto");
        register(result, "listDishes", AgentQueryDomain.DISH, AgentQueryAction.LIST, "dish:list", 20, "dishIds<=20", "AgentDishSummaryDto[]");
        register(result, "listScheduledDishes", AgentQueryDomain.DISH, AgentQueryAction.LIST, "mealPlan:list+dish:list", 20, "recordDate|mealTypes(LUNCH,DINNER)", "AgentScheduledMenuResponseDto");
        register(result, "previewDishCandidates", AgentQueryDomain.DISH, AgentQueryAction.LIST, "customerProfile:list+customerOrder:list+package:list+dish:list", 20, "customerId|recordDate|mealType", "AgentDishCandidatePreviewDto");
        register(result, "explainRule", AgentQueryDomain.BUSINESS_RULE, AgentQueryAction.EXPLAIN, "agentDiagnosis:list", 1, "topic", "AgentBusinessRuleDto");
        register(result, "getDailyCustomerWorkload", AgentQueryDomain.OPERATION_STATISTICS, AgentQueryAction.SUMMARY, "mealPlan:list", 100, "recordDate|mealType", "AgentDailyCustomerStatsDto");
        register(result, "getCustomerProfileCount", AgentQueryDomain.OPERATION_STATISTICS, AgentQueryAction.SUMMARY, "customerProfile:list", 1, "none", "AgentOperationCountDto");
        register(result, "getActiveCustomerSummary", AgentQueryDomain.OPERATION_STATISTICS, AgentQueryAction.SUMMARY, "customerOrder:list", 1, "dateRange", "AgentOperationCountDto");
        register(result, "listActiveCustomerMealBalances", AgentQueryDomain.OPERATION_STATISTICS, AgentQueryAction.BREAKDOWN, "customerOrder:list", 50, "activeCustomerSet|page|size", "ActiveCustomerBalanceResponse");
        register(result, "getExpiringOrderSummary", AgentQueryDomain.OPERATION_STATISTICS, AgentQueryAction.SUMMARY, "customerOrder:list", 1, "startDate|endDate", "AgentOperationCountDto");
        register(result, "getMealPlanFailureSummary", AgentQueryDomain.OPERATION_STATISTICS, AgentQueryAction.SUMMARY, "mealPlan:list", 1, "recordDate|mealType", "AgentDailyCustomerStatsDto");
        return Map.copyOf(result);
    }

    private static void register(Map<String, AgentBusinessToolDescriptor> target, String name, AgentQueryDomain domain,
                                 AgentQueryAction action, String permission, int maxResults, String inputSchema, String outputSchema) {
        target.put(name, new AgentBusinessToolDescriptor(name, domain, action, permission, maxResults,
            "INTERNAL_READ_ONLY", inputSchema, outputSchema, 3000));
    }
}
