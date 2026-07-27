package me.zhengjie.agent.tool;

import me.zhengjie.agent.query.tool.AgentBusinessToolDescriptor;
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 工具描述的唯一权威来源；旧注册表仅作为兼容门面读取此目录。 */
public final class ToolCatalog {
    private static final Map<String, AgentBusinessToolDescriptor> BUSINESS_TOOLS = buildBusinessTools();
    private ToolCatalog() { }
    public static boolean isRegistered(String name) { return name != null && BUSINESS_TOOLS.containsKey(name); }
    public static AgentBusinessToolDescriptor descriptor(String name) { return name == null ? null : BUSINESS_TOOLS.get(name); }
    public static Collection<AgentBusinessToolDescriptor> descriptors() { return BUSINESS_TOOLS.values(); }

    /**
     * 执行已登记的遗留只读工具。名称到传输适配器的映射只保留在此基础设施目录，
     * 调用方不能再维护第二套 if/switch 分发链。
     */
    public static java.util.Map<String, Object> execute(BusinessQueryDataClient client, AgentQueryPlan plan, String toolName,
                                                         String ruleTopic, List<Integer> dishIds) {
        if (!isRegistered(toolName)) throw new IllegalArgumentException("unsupported tool");
        AgentEntityReference entities = plan.getEntities();
        AgentQueryFilters filters = plan.getFilters();
        if ("resolveCustomer".equals(toolName)) return client.resolveCustomerTyped(entities.getCustomerId(), entities.getCustomerCode(), entities.getCustomerName()).toPresentationMap();
        if ("customerOverview".equals(toolName)) return client.customerOverviewTyped(entities.getCustomerId(), entities.getCustomerCode()).toPresentationMap();
        if ("listOrders".equals(toolName)) return client.listOrdersTyped(entities.getCustomerId(), orderStatus(filters), page(filters), size(filters)).toPresentationMap();
        if ("orderDetail".equals(toolName)) return client.orderDetailTyped(entities.getOrderId(), entities.getOrderCode(), entities.getCustomerId()).toPresentationMap();
        if ("listMealPlans".equals(toolName)) return client.listMealPlansTyped(entities.getCustomerId(), filters.getRecordDate(), filters.getStartDate(), filters.getEndDate(), filters.getMealType(), entities.getMealPlanRecordId(), page(filters), size(filters)).toPresentationMap();
        if ("listVerifications".equals(toolName)) return client.listVerificationsTyped(entities.getCustomerId(), entities.getOrderId(), filters.getMealType(), recentLimit(filters), filters.getStartDate(), filters.getEndDate()).toPresentationMap();
        if ("listRefunds".equals(toolName)) return client.listRefundsTyped(entities.getCustomerId(), entities.getOrderId(), recentLimit(filters), filters.getStartDate(), filters.getEndDate()).toPresentationMap();
        if ("packageDetail".equals(toolName)) return client.packageDetailTyped(entities.getPackageId()).toPresentationMap();
        if ("explainRule".equals(toolName)) return client.explainRuleTyped(ruleTopic).toPresentationMap();
        if ("listDishes".equals(toolName)) return client.listDishesTyped(dishIds == null ? List.of() : dishIds.stream().distinct().limit(20).toList()).toPresentationMap();
        if ("listScheduledDishes".equals(toolName)) return client.listScheduledDishes(filters.getRecordDate(), scheduledMenuMealTypes(plan));
        if ("previewDishCandidates".equals(toolName)) return client.previewDishCandidates(entities.getCustomerId(), filters.getRecordDate(), filters.getMealType()).toPresentationMap();
        if ("getDailyCustomerWorkload".equals(toolName) || "getMealPlanFailureSummary".equals(toolName)) return client.dailyCustomerWorkload(filters.getRecordDate(), filters.getMealType(), plan.getDimensions() == null ? List.of() : plan.getDimensions().stream().map(Enum::name).collect(java.util.stream.Collectors.toList()));
        if ("getCustomerProfileCount".equals(toolName)) return client.customerProfileCount();
        if ("getActiveCustomerSummary".equals(toolName)) return client.activeCustomerSummary();
        if ("listActiveCustomerMealBalances".equals(toolName)) return client.activeCustomerBalances(page(filters), size(filters)).toPresentationMap();
        if ("getExpiringOrderSummary".equals(toolName)) return client.expiringOrderSummary(filters.getStartDate(), filters.getEndDate());
        throw new IllegalArgumentException("unsupported tool");
    }
    private static int page(AgentQueryFilters filters) { return filters == null || filters.getPage() == null ? 1 : filters.getPage(); }
    private static int size(AgentQueryFilters filters) { return filters == null || filters.getSize() == null ? 10 : filters.getSize(); }
    private static int recentLimit(AgentQueryFilters filters) { return filters == null || filters.getRecentLimit() == null ? 10 : filters.getRecentLimit(); }
    private static Integer orderStatus(AgentQueryFilters filters) { try { return filters == null || filters.getOrderStatus() == null ? null : Integer.valueOf(filters.getOrderStatus()); } catch (NumberFormatException ignored) { return null; } }
    private static List<String> scheduledMenuMealTypes(AgentQueryPlan plan) {
        if (plan != null && plan.getMealScope() == me.zhengjie.agent.analysis.domain.MealScope.LUNCH) return List.of("LUNCH");
        if (plan != null && plan.getMealScope() == me.zhengjie.agent.analysis.domain.MealScope.DINNER) return List.of("DINNER");
        AgentQueryFilters filters = plan == null ? null : plan.getFilters();
        if (filters != null && "LUNCH".equals(filters.getMealType())) return List.of("LUNCH");
        if (filters != null && "DINNER".equals(filters.getMealType())) return List.of("DINNER");
        return List.of("LUNCH", "DINNER");
    }

    private static Map<String, AgentBusinessToolDescriptor> buildBusinessTools() {
        Map<String, AgentBusinessToolDescriptor> result = new LinkedHashMap<>();
        register(result, "resolveCustomer", "CUSTOMER", "LIST", "customerProfile:list", 10, "customerId|customerCode|customerName", "AgentCustomerCandidateDto[]");
        register(result, "customerOverview", "CUSTOMER", "OVERVIEW", "customerProfile:list", 1, "customerId|customerCode", "AgentCustomerOverviewDto");
        register(result, "listOrders", "ORDER", "LIST", "customerOrder:list", 20, "customerId|status|page|size", "AgentOrderSummaryDto[]");
        register(result, "orderDetail", "ORDER", "DETAIL", "customerOrder:list", 1, "orderId|orderCode|customerId", "AgentOrderSummaryDto");
        register(result, "listMealPlans", "MEAL_PLAN", "LIST", "mealPlan:list", 50, "customerId?|recordDate?|startDate?|endDate?|mealType?|customerMealPlanId?|page?|size?", "AgentMealPlanSummaryDto[]");
        register(result, "listVerifications", "VERIFICATION", "LIST", "mealPlan:list", 50, "customerId|orderId|mealType|limit", "AgentVerificationLogDto[]");
        register(result, "listRefunds", "REFUND", "LIST", "customerOrder:list+mealPlan:list", 50, "customerId|orderId|limit", "AgentRefundLogDto[]");
        register(result, "packageDetail", "PACKAGE", "DETAIL", "package:list", 5, "parentPackageId", "AgentPackageSpecDto");
        register(result, "listDishes", "DISH", "LIST", "dish:list", 20, "dishIds<=20", "AgentDishSummaryDto[]");
        register(result, "listScheduledDishes", "DISH", "LIST", "mealPlan:list+dish:list", 20, "recordDate|mealTypes(LUNCH,DINNER)", "AgentScheduledMenuResponseDto");
        register(result, "previewDishCandidates", "DISH", "LIST", "customerProfile:list+customerOrder:list+package:list+dish:list", 20, "customerId|recordDate|mealType", "AgentDishCandidatePreviewDto");
        register(result, "explainRule", "BUSINESS_RULE", "EXPLAIN", "agentDiagnosis:list", 1, "topic", "AgentBusinessRuleDto");
        register(result, "getDailyCustomerWorkload", "OPERATION_STATISTICS", "SUMMARY", "mealPlan:list", 100, "recordDate|mealType", "AgentDailyCustomerStatsDto");
        register(result, "getCustomerProfileCount", "OPERATION_STATISTICS", "SUMMARY", "customerProfile:list", 1, "none", "AgentOperationCountDto");
        register(result, "getActiveCustomerSummary", "OPERATION_STATISTICS", "SUMMARY", "customerOrder:list", 1, "dateRange", "AgentOperationCountDto");
        register(result, "listActiveCustomerMealBalances", "OPERATION_STATISTICS", "BREAKDOWN", "customerOrder:list", 50, "activeCustomerSet|page|size", "ActiveCustomerBalanceResponse");
        register(result, "getExpiringOrderSummary", "OPERATION_STATISTICS", "SUMMARY", "customerOrder:list", 1, "startDate|endDate", "AgentOperationCountDto");
        register(result, "getMealPlanFailureSummary", "OPERATION_STATISTICS", "SUMMARY", "mealPlan:list", 1, "recordDate|mealType", "AgentDailyCustomerStatsDto");
        return Map.copyOf(result);
    }
    private static void register(Map<String, AgentBusinessToolDescriptor> result, String name, String domain, String action,
                                 String permission, int max, String input, String output) {
        result.put(name, new AgentBusinessToolDescriptor(name,
            me.zhengjie.agent.query.domain.AgentQueryDomain.valueOf(domain), me.zhengjie.agent.query.domain.AgentQueryAction.valueOf(action),
            permission, max, "INTERNAL_READ_ONLY", input, output, 3000));
    }
}
