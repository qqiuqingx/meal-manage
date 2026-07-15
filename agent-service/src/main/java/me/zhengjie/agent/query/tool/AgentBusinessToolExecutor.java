package me.zhengjie.agent.query.tool;

import me.zhengjie.agent.query.AgentQueryPlanValidationResult;
import me.zhengjie.agent.query.AgentQueryPlanValidator;
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.client.BusinessQueryClientException;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.security.AgentAccessContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 受控业务工具执行器：仅执行 QueryPlan 登记的内部只读工具，并在单轮内复用同参结果。
 */
public class AgentBusinessToolExecutor {
    private static final int MAX_CALLS = 6;
    private static final int MAX_DATA_ITEMS = 100;
    private final BusinessQueryDataClient client;
    private final AgentQueryPlanValidator validator;
    private final Map<String, Map<String, Object>> roundCache = new LinkedHashMap<>();
    private int callCount;
    /** 本轮已按受控 QueryPlan 返回上限预留的业务条目数量，避免组合工具超出模型上下文预算。 */
    private int reservedDataItems;

    /**
     * 创建单轮工具执行器；每一个聊天请求应创建一个实例，避免跨请求缓存业务数据。
     *
     * @param client 主系统受控业务查询客户端
     * @param validator QueryPlan 校验器
     */
    public AgentBusinessToolExecutor(BusinessQueryDataClient client, AgentQueryPlanValidator validator) {
        this.client = client;
        this.validator = validator;
    }

    /**
     * 执行计划中的指定已登记工具。
     *
     * @param plan 已解析的受控查询计划
     * @param toolName 要执行的白名单工具名
     * @param ruleTopic 规则主题（仅 explainRule 使用）
     * @param dishIds 菜品 ID（仅 listDishes 使用）
     * @return 查询结果、缓存命中及可控告警
     */
    public ToolExecutionResult execute(AgentQueryPlan plan, String toolName, String ruleTopic, List<Integer> dishIds) {
        AgentQueryPlanValidationResult validation = validator.validate(plan);
        if (!validation.isValid() || plan.getToolNames() == null || !plan.getToolNames().contains(toolName)
            || !AgentBusinessToolRegistry.isRegistered(toolName)) {
            return ToolExecutionResult.failure("PLAN_INVALID");
        }
        if (!AgentBusinessToolRegistry.isAvailable(toolName, AgentAccessContextHolder.availableTools())) {
            return ToolExecutionResult.failure("TOOL_PERMISSION_DENIED");
        }
        String key = cacheKey(plan, toolName, ruleTopic, dishIds);
        if (roundCache.containsKey(key)) return ToolExecutionResult.cached(roundCache.get(key));
        if (callCount >= MAX_CALLS) return ToolExecutionResult.failure("TOOL_BUDGET_EXCEEDED");
        AgentBusinessToolDescriptor descriptor = AgentBusinessToolRegistry.descriptor(toolName);
        int reservedItems = reservedItemCount(plan, toolName, dishIds, descriptor);
        if (descriptor == null || reservedDataItems + reservedItems > MAX_DATA_ITEMS) {
            return ToolExecutionResult.failure("TOOL_DATA_BUDGET_EXCEEDED");
        }
        try {
            Map<String, Object> result = invoke(plan, toolName, ruleTopic, dishIds);
            Map<String, Object> safe = result == null ? Map.of() : result;
            roundCache.put(key, safe);
            callCount++;
            reservedDataItems += reservedItems;
            return ToolExecutionResult.success(safe);
        } catch (BusinessQueryClientException exception) {
            return ToolExecutionResult.failure(exception.getFailureCode());
        } catch (RuntimeException exception) {
            return ToolExecutionResult.failure("TOOL_CALL_FAILED");
        }
    }

    /** 返回本轮实际内部工具调用次数。 */
    public int getCallCount() { return callCount; }

    /** 返回本轮按登记工具上限预留的数据条目数。 */
    public int getReservedDataItems() { return reservedDataItems; }

    /**
     * 按本次计划的分页、最近条数或菜品 ID 数量预留数据预算，始终不超过工具登记上限。
     *
     * @param plan 已通过校验的查询计划
     * @param toolName 工具名称
     * @param dishIds 菜品 ID 列表
     * @param descriptor 工具登记描述
     * @return 本次调用最多返回的受控条目数
     */
    private int reservedItemCount(AgentQueryPlan plan, String toolName, List<Integer> dishIds,
                                  AgentBusinessToolDescriptor descriptor) {
        if (descriptor == null) return MAX_DATA_ITEMS + 1;
        AgentQueryFilters filters = plan.getFilters();
        int requested = descriptor.maxResults();
        if ("listOrders".equals(toolName)) requested = size(filters);
        else if ("listVerifications".equals(toolName) || "listRefunds".equals(toolName)) requested = recentLimit(filters);
        else if ("listDishes".equals(toolName) && dishIds != null) requested = dishIds.stream().distinct().limit(20).toList().size();
        return Math.min(Math.max(requested, 0), descriptor.maxResults());
    }

    private Map<String, Object> invoke(AgentQueryPlan plan, String toolName, String ruleTopic, List<Integer> dishIds) {
        AgentEntityReference entities = plan.getEntities();
        AgentQueryFilters filters = plan.getFilters();
        if ("resolveCustomer".equals(toolName)) return client.resolveCustomerTyped(entities.getCustomerId(), entities.getCustomerCode(), entities.getCustomerName()).toPresentationMap();
        if ("customerOverview".equals(toolName)) return client.customerOverviewTyped(entities.getCustomerId(), entities.getCustomerCode()).toPresentationMap();
        if ("listOrders".equals(toolName)) return client.listOrdersTyped(entities.getCustomerId(), orderStatus(filters), page(filters), size(filters)).toPresentationMap();
        if ("orderDetail".equals(toolName)) return client.orderDetailTyped(entities.getOrderId(), entities.getOrderCode(), entities.getCustomerId()).toPresentationMap();
        if ("listMealPlans".equals(toolName)) {
            if (AgentQueryPlan.SCHEMA_VERSION_V3.equals(plan.getVersion())) {
                return client.listMealPlansRangeTyped(entities.getCustomerId(), filters.getRecordDate(), filters.getMealType(), page(filters), size(filters)).toPresentationMap();
            }
            return client.listMealPlansTyped(entities.getCustomerId(), filters.getRecordDate(), filters.getMealType(), entities.getMealPlanRecordId()).toPresentationMap();
        }
        if ("listVerifications".equals(toolName)) return client.listVerificationsTyped(entities.getCustomerId(), entities.getOrderId(), filters.getMealType(), recentLimit(filters), filters.getStartDate(), filters.getEndDate()).toPresentationMap();
        if ("listRefunds".equals(toolName)) return client.listRefundsTyped(entities.getCustomerId(), entities.getOrderId(), recentLimit(filters), filters.getStartDate(), filters.getEndDate()).toPresentationMap();
        if ("packageDetail".equals(toolName)) return client.packageDetailTyped(entities.getPackageId()).toPresentationMap();
        if ("explainRule".equals(toolName)) return client.explainRuleTyped(ruleTopic).toPresentationMap();
        if ("listDishes".equals(toolName)) return client.listDishesTyped(dishIds == null ? List.of() : dishIds.stream().distinct().limit(20).toList()).toPresentationMap();
        if ("listScheduledDishes".equals(toolName)) return client.listScheduledDishes(filters.getRecordDate(), scheduledMenuMealTypes(plan));
        if ("previewDishCandidates".equals(toolName)) return client.previewDishCandidates(entities.getCustomerId(), filters.getRecordDate(), filters.getMealType()).toPresentationMap();
        if ("getDailyCustomerWorkload".equals(toolName) || "getMealPlanFailureSummary".equals(toolName)) {
            List<String> dimensions = plan.getDimensions() == null ? List.of() : plan.getDimensions().stream().map(Enum::name).collect(java.util.stream.Collectors.toList());
            return client.dailyCustomerWorkload(filters.getRecordDate(), filters.getMealType(), dimensions);
        }
        if ("getCustomerProfileCount".equals(toolName)) return client.customerProfileCount();
        if ("getActiveCustomerSummary".equals(toolName)) return client.activeCustomerSummary();
        if ("listActiveCustomerMealBalances".equals(toolName)) return client.activeCustomerBalances(page(filters), size(filters)).toPresentationMap();
        if ("getExpiringOrderSummary".equals(toolName)) return client.expiringOrderSummary(filters.getStartDate(), filters.getEndDate());
        throw new IllegalArgumentException("unsupported tool");
    }

    private int page(AgentQueryFilters filters) { return filters.getPage() == null ? 1 : filters.getPage(); }
    private int size(AgentQueryFilters filters) { return filters == null || filters.getSize() == null ? 10 : filters.getSize(); }
    private int recentLimit(AgentQueryFilters filters) { return filters == null || filters.getRecentLimit() == null ? 10 : filters.getRecentLimit(); }
    /** 将受控状态文本转换为内部订单接口使用的状态码，非数字状态不下发。 */
    private Integer orderStatus(AgentQueryFilters filters) {
        try { return filters.getOrderStatus() == null ? null : Integer.valueOf(filters.getOrderStatus()); }
        catch (NumberFormatException ignored) { return null; }
    }

    /** 公共菜单未指定餐次时固定查询午餐和晚餐，禁止将空餐次传入单餐次 SQL。 */
    private List<String> scheduledMenuMealTypes(AgentQueryPlan plan) {
        if (plan != null && plan.getMealScope() == me.zhengjie.agent.analysis.domain.MealScope.LUNCH) return List.of("LUNCH");
        if (plan != null && plan.getMealScope() == me.zhengjie.agent.analysis.domain.MealScope.DINNER) return List.of("DINNER");
        AgentQueryFilters filters = plan == null ? null : plan.getFilters();
        if (filters != null && "LUNCH".equals(filters.getMealType())) return List.of("LUNCH");
        if (filters != null && "DINNER".equals(filters.getMealType())) return List.of("DINNER");
        return List.of("LUNCH", "DINNER");
    }
    private String cacheKey(AgentQueryPlan plan, String toolName, String ruleTopic, List<Integer> dishIds) {
        return toolName + "|" + plan.getEntities().getCustomerId() + "|" + plan.getEntities().getCustomerCode() + "|"
            + plan.getEntities().getCustomerName() + "|" + plan.getEntities().getOrderId() + "|" + plan.getEntities().getOrderCode() + "|" + plan.getFilters().getRecordDate()
            + "|" + plan.getEntities().getMealPlanRecordId() + "|" + plan.getFilters().getMealType() + "|" + plan.getFilters().getStartDate() + "|" + plan.getFilters().getEndDate() + "|" + ruleTopic + "|" + new ArrayList<>(dishIds == null ? List.of() : dishIds);
    }

    /** 单次工具执行的受控结果，禁止透传异常详情或原始请求。 */
    public record ToolExecutionResult(Map<String, Object> result, boolean cached, boolean partial, List<String> warnings) {
        public static ToolExecutionResult success(Map<String, Object> result) { return new ToolExecutionResult(result, false, false, List.of()); }
        public static ToolExecutionResult cached(Map<String, Object> result) { return new ToolExecutionResult(result, true, false, List.of()); }
        public static ToolExecutionResult failure(String warning) { return new ToolExecutionResult(Map.of(), false, true, List.of(warning)); }
    }
}
