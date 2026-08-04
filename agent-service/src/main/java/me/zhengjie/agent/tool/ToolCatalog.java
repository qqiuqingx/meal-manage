package me.zhengjie.agent.tool;

import me.zhengjie.agent.query.tool.AgentBusinessToolDescriptor;
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryPlan;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工具描述的唯一权威来源；旧注册表仅作为兼容门面读取此目录。 */
public final class ToolCatalog {
    public static final String RESOLVE_CUSTOMER = "resolveCustomer";
    public static final String CUSTOMER_OVERVIEW = "customerOverview";
    public static final String LIST_ORDERS = "listOrders";
    public static final String ORDER_DETAIL = "orderDetail";
    public static final String LIST_MEAL_PLANS = "listMealPlans";
    public static final String LIST_VERIFICATIONS = "listVerifications";
    public static final String LIST_REFUNDS = "listRefunds";
    public static final String PACKAGE_DETAIL = "packageDetail";
    public static final String LIST_DISHES = "listDishes";
    public static final String LIST_SCHEDULED_DISHES = "listScheduledDishes";
    public static final String PREVIEW_DISH_CANDIDATES = "previewDishCandidates";
    public static final String EXPLAIN_RULE = "explainRule";
    public static final String GET_DAILY_CUSTOMER_WORKLOAD = "getDailyCustomerWorkload";
    public static final String GET_CUSTOMER_PROFILE_COUNT = "getCustomerProfileCount";
    public static final String GET_ACTIVE_CUSTOMER_SUMMARY = "getActiveCustomerSummary";
    public static final String GET_ACTIVE_ORDER_SUMMARY = "getActiveOrderSummary";
    public static final String LIST_ACTIVE_CUSTOMER_MEAL_BALANCES =
        "listActiveCustomerMealBalances";
    public static final String GET_EXPIRING_ORDER_SUMMARY = "getExpiringOrderSummary";
    public static final String GET_MEAL_PLAN_FAILURE_SUMMARY = "getMealPlanFailureSummary";

    public static final String GET_CUSTOMER_PROFILE = "getCustomerProfile";
    public static final String LIST_CUSTOMER_ORDERS = "listCustomerOrders";
    public static final String GET_MEAL_PLAN = "getMealPlan";
    public static final String GET_CANDIDATE_DISH_STATS = "getCandidateDishStats";
    public static final String GET_CUSTOMER_EXCLUDE_DATES = "getCustomerExcludeDates";
    public static final String GET_ORDER_MEAL_BALANCE = "getOrderMealBalance";
    public static final String GET_PACKAGE_SPEC = "getPackageSpec";
    public static final String GET_DISH_CANDIDATE_DETAIL = "getDishCandidateDetail";
    public static final String LIST_VERIFICATION_LOGS = "listVerificationLogs";
    public static final String LIST_MEAL_REFUNDS = "listMealRefunds";
    public static final String GET_MEAL_PLAN_GENERATION_SNAPSHOT = "getMealPlanGenerationSnapshot";

    private static final Map<String, AgentBusinessToolDescriptor> BUSINESS_TOOLS = buildBusinessTools();
    private static final Map<String, BusinessToolInvoker> BUSINESS_TOOL_INVOKERS = buildBusinessToolInvokers();
    private static final Map<String, ToolDescriptor> DIAGNOSIS_TOOLS = buildDiagnosisTools();

    static {
        if (!BUSINESS_TOOLS.keySet().equals(BUSINESS_TOOL_INVOKERS.keySet())) {
            throw new IllegalStateException("Business tool descriptors and invokers are inconsistent");
        }
    }

    private ToolCatalog() { }
    public static boolean isRegistered(String name) { return name != null && BUSINESS_TOOLS.containsKey(name); }
    public static AgentBusinessToolDescriptor descriptor(String name) { return name == null ? null : BUSINESS_TOOLS.get(name); }
    public static Collection<AgentBusinessToolDescriptor> descriptors() { return BUSINESS_TOOLS.values(); }

    /** 判断工具是否同时具备已登记的执行适配器。 */
    public static boolean isExecutable(String name) { return name != null && BUSINESS_TOOL_INVOKERS.containsKey(name); }

    public static boolean isDiagnosisToolRegistered(String name) {
        return name != null && DIAGNOSIS_TOOLS.containsKey(name);
    }
    public static Collection<ToolDescriptor> diagnosisDescriptors() {
        return DIAGNOSIS_TOOLS.values();
    }

    /**
     * 执行已登记的遗留只读工具。名称到传输适配器的映射只保留在此基础设施目录，
     * 调用方不能再维护第二套 if/switch 分发链。
     */
    public static java.util.Map<String, Object> execute(BusinessQueryDataClient client, AgentQueryPlan plan, String toolName,
                                                         String ruleTopic, List<Integer> dishIds) {
        BusinessToolInvoker invoker = BUSINESS_TOOL_INVOKERS.get(toolName);
        if (invoker == null) throw new IllegalArgumentException("unsupported tool");
        return invoker.invoke(client, new BusinessToolInvocation(plan, ruleTopic,
            dishIds == null ? List.of() : dishIds));
    }

    /**
     * 建立工具名到强类型传输适配器的唯一映射。中心执行方法只查表，不再随工具数量增长分支。
     */
    private static Map<String, BusinessToolInvoker> buildBusinessToolInvokers() {
        Map<String, BusinessToolInvoker> result = new LinkedHashMap<>();
        result.put(RESOLVE_CUSTOMER, (client, call) -> client.resolveCustomerTyped(call.entities().getCustomerId(),
            call.entities().getCustomerCode(), call.entities().getCustomerName()).toPresentationMap());
        result.put(CUSTOMER_OVERVIEW, (client, call) -> client.customerOverviewTyped(call.entities().getCustomerId(),
            call.entities().getCustomerCode()).toPresentationMap());
        result.put(LIST_ORDERS, (client, call) -> client.listOrdersTyped(call.entities().getCustomerId(),
            orderStatus(call.filters()), page(call.filters()), size(call.filters())).toPresentationMap());
        result.put(ORDER_DETAIL, (client, call) -> client.orderDetailTyped(call.entities().getOrderId(),
            call.entities().getOrderCode(), call.entities().getCustomerId()).toPresentationMap());
        result.put(LIST_MEAL_PLANS, (client, call) -> client.listMealPlansTyped(call.entities().getCustomerId(),
            call.filters().getRecordDate(), call.filters().getStartDate(), call.filters().getEndDate(),
            call.filters().getMealType(), call.entities().getMealPlanRecordId(), page(call.filters()),
            size(call.filters())).toPresentationMap());
        result.put(LIST_VERIFICATIONS, (client, call) -> client.listVerificationsTyped(call.entities().getCustomerId(),
            call.entities().getOrderId(), call.filters().getMealType(), recentLimit(call.filters()),
            call.filters().getStartDate(), call.filters().getEndDate()).toPresentationMap());
        result.put(LIST_REFUNDS, (client, call) -> client.listRefundsTyped(call.entities().getCustomerId(),
            call.entities().getOrderId(), recentLimit(call.filters()), call.filters().getStartDate(),
            call.filters().getEndDate()).toPresentationMap());
        result.put(PACKAGE_DETAIL, (client, call) ->
            client.packageDetailTyped(call.entities().getPackageId()).toPresentationMap());
        result.put(EXPLAIN_RULE, (client, call) -> client.explainRuleTyped(call.ruleTopic()).toPresentationMap());
        result.put(LIST_DISHES, (client, call) -> client.listDishesTyped(
            call.dishIds().stream().distinct().limit(20).toList()).toPresentationMap());
        result.put(LIST_SCHEDULED_DISHES, (client, call) -> client.listScheduledDishes(
            call.filters().getRecordDate(), scheduledMenuMealTypes(call.plan())));
        result.put(PREVIEW_DISH_CANDIDATES, (client, call) -> client.previewDishCandidates(
            call.entities().getCustomerId(), call.filters().getRecordDate(),
            call.filters().getMealType()).toPresentationMap());
        BusinessToolInvoker workload = (client, call) -> client.dailyCustomerWorkload(
            call.filters().getRecordDate(), call.filters().getMealType(),
            call.plan().getDimensions() == null ? List.of() : call.plan().getDimensions().stream()
                .map(Enum::name).collect(java.util.stream.Collectors.toList()));
        result.put(GET_DAILY_CUSTOMER_WORKLOAD, workload);
        result.put(GET_MEAL_PLAN_FAILURE_SUMMARY, workload);
        result.put(GET_CUSTOMER_PROFILE_COUNT, (client, call) -> client.customerProfileCount());
        result.put(GET_ACTIVE_CUSTOMER_SUMMARY, (client, call) -> client.activeCustomerSummary());
        result.put(GET_ACTIVE_ORDER_SUMMARY, (client, call) -> client.activeOrderSummary());
        result.put(LIST_ACTIVE_CUSTOMER_MEAL_BALANCES, (client, call) ->
            client.activeCustomerBalances(page(call.filters()), size(call.filters())).toPresentationMap());
        result.put(GET_EXPIRING_ORDER_SUMMARY, (client, call) ->
            client.expiringOrderSummary(call.filters().getStartDate(), call.filters().getEndDate()));
        return Map.copyOf(result);
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

    @FunctionalInterface
    private interface BusinessToolInvoker {
        Map<String, Object> invoke(BusinessQueryDataClient client, BusinessToolInvocation call);
    }

    /** 单次目录执行的受控参数；实体和过滤条件只来自已校验 QueryPlan。 */
    private record BusinessToolInvocation(AgentQueryPlan plan, String ruleTopic, List<Integer> dishIds) {
        /** 返回计划中已校验的实体引用。 */
        private AgentEntityReference entities() { return plan.getEntities(); }

        /** 返回计划中已校验的过滤条件。 */
        private AgentQueryFilters filters() { return plan.getFilters(); }
    }

    private static Map<String, AgentBusinessToolDescriptor> buildBusinessTools() {
        Map<String, AgentBusinessToolDescriptor> result = new LinkedHashMap<>();
        register(result, RESOLVE_CUSTOMER, "CUSTOMER", "LIST", "customerProfile:list", 10, "customerId|customerCode|customerName", "AgentCustomerCandidateDto[]");
        register(result, CUSTOMER_OVERVIEW, "CUSTOMER", "OVERVIEW", "customerProfile:list", 1, "customerId|customerCode", "AgentCustomerOverviewDto");
        register(result, LIST_ORDERS, "ORDER", "LIST", "customerOrder:list", 20, "customerId?|status|page|size", "AgentOrderSummaryDto[]");
        register(result, ORDER_DETAIL, "ORDER", "DETAIL", "customerOrder:list", 1, "orderId|orderCode|customerId", "AgentOrderSummaryDto");
        register(result, LIST_MEAL_PLANS, "MEAL_PLAN", "LIST", "mealPlan:list", 50, "customerId?|recordDate?|startDate?|endDate?|mealType?|customerMealPlanId?|page?|size?", "AgentMealPlanSummaryDto[]");
        register(result, LIST_VERIFICATIONS, "VERIFICATION", "LIST", "mealPlan:list", 50, "customerId|orderId|mealType|limit", "AgentVerificationLogDto[]");
        register(result, LIST_REFUNDS, "REFUND", "LIST", "customerOrder:list+mealPlan:list", 50, "customerId|orderId|limit", "AgentRefundLogDto[]");
        register(result, PACKAGE_DETAIL, "PACKAGE", "DETAIL", "package:list", 5, "parentPackageId", "AgentPackageSpecDto");
        register(result, LIST_DISHES, "DISH", "LIST", "dish:list", 20, "dishIds<=20", "AgentDishSummaryDto[]");
        register(result, LIST_SCHEDULED_DISHES, "DISH", "LIST", "mealPlan:list+dish:list", 20, "recordDate|mealTypes(LUNCH,DINNER)", "AgentScheduledMenuResponseDto");
        register(result, PREVIEW_DISH_CANDIDATES, "DISH", "LIST", "customerProfile:list+customerOrder:list+package:list+dish:list", 20, "customerId|recordDate|mealType", "AgentDishCandidatePreviewDto");
        register(result, EXPLAIN_RULE, "BUSINESS_RULE", "EXPLAIN", "agentDiagnosis:list", 1, "topic", "AgentBusinessRuleDto");
        register(result, GET_DAILY_CUSTOMER_WORKLOAD, "OPERATION_STATISTICS", "SUMMARY", "mealPlan:list", 100, "recordDate|mealType", "AgentDailyCustomerStatsDto");
        register(result, GET_CUSTOMER_PROFILE_COUNT, "OPERATION_STATISTICS", "SUMMARY", "customerProfile:list", 1, "none", "AgentOperationCountDto");
        register(result, GET_ACTIVE_CUSTOMER_SUMMARY, "OPERATION_STATISTICS", "SUMMARY", "customerOrder:list", 1, "dateRange", "AgentOperationCountDto");
        register(result, GET_ACTIVE_ORDER_SUMMARY, "OPERATION_STATISTICS", "SUMMARY", "customerOrder:list", 1, "authorizedScope", "AgentOperationCountDto");
        register(result, LIST_ACTIVE_CUSTOMER_MEAL_BALANCES, "OPERATION_STATISTICS", "BREAKDOWN", "customerOrder:list", 50, "activeCustomerSet|page|size", "ActiveCustomerBalanceResponse");
        register(result, GET_EXPIRING_ORDER_SUMMARY, "OPERATION_STATISTICS", "SUMMARY", "customerOrder:list", 1, "startDate|endDate", "AgentOperationCountDto");
        register(result, GET_MEAL_PLAN_FAILURE_SUMMARY, "OPERATION_STATISTICS", "SUMMARY", "mealPlan:list", 1, "recordDate|mealType", "AgentDailyCustomerStatsDto");
        return Map.copyOf(result);
    }

    private static Map<String, ToolDescriptor> buildDiagnosisTools() {
        Map<String, ToolDescriptor> result = new LinkedHashMap<>();
        registerDiagnosis(result, GET_CUSTOMER_PROFILE, "CUSTOMER", "DETAIL", "CustomerLookup", "CustomerProfile");
        registerDiagnosis(result, LIST_CUSTOMER_ORDERS, "ORDER", "LIST", "CustomerOrders", "OrderSummary[]");
        registerDiagnosis(result, GET_MEAL_PLAN, "MEAL_PLAN", "DETAIL", "MealPlanLookup", "MealPlan");
        registerDiagnosis(result, GET_CANDIDATE_DISH_STATS, "DISH", "SUMMARY", "CandidateDishStats", "CandidateDishStats[]");
        registerDiagnosis(result, GET_CUSTOMER_EXCLUDE_DATES, "CUSTOMER", "DETAIL", "CustomerLookup", "CustomerExcludeDates");
        registerDiagnosis(result, GET_ORDER_MEAL_BALANCE, "ORDER", "SUMMARY", "CustomerOrders", "OrderMealBalance");
        registerDiagnosis(result, GET_PACKAGE_SPEC, "PACKAGE", "DETAIL", "PackageSpec", "PackageSpec");
        registerDiagnosis(result, GET_DISH_CANDIDATE_DETAIL, "DISH", "LIST", "CandidateDishStats", "DishCandidateDetail[]");
        registerDiagnosis(result, LIST_VERIFICATION_LOGS, "VERIFICATION", "LIST", "VerificationLogs", "VerificationLog[]");
        registerDiagnosis(result, LIST_MEAL_REFUNDS, "REFUND", "LIST", "MealRefunds", "MealRefund[]");
        registerDiagnosis(result, GET_MEAL_PLAN_GENERATION_SNAPSHOT, "MEAL_PLAN", "SUMMARY", "MealPlanLookup", "GenerationSnapshot");
        return Map.copyOf(result);
    }

    private static void registerDiagnosis(Map<String, ToolDescriptor> result, String name, String domain,
                                          String action, String input, String output) {
        result.put(name, new ToolDescriptor(name, domain, action, "agentDiagnosis:list", 50, 3000,
            "INTERNAL", true, "v1", "v1", input, output));
    }

    private static void register(Map<String, AgentBusinessToolDescriptor> result, String name, String domain, String action,
                                 String permission, int max, String input, String output) {
        result.put(name, new AgentBusinessToolDescriptor(name,
            me.zhengjie.agent.query.domain.AgentQueryDomain.valueOf(domain), me.zhengjie.agent.query.domain.AgentQueryAction.valueOf(action),
            permission, max, "INTERNAL_READ_ONLY", input, output, 3000));
    }
}
