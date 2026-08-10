package me.zhengjie.agent.tool;

import me.zhengjie.agent.tool.input.ExplainBusinessRuleInput;
import me.zhengjie.agent.tool.input.GetPackageDetailInput;
import me.zhengjie.agent.tool.input.GetServiceCustomerDetailInput;
import me.zhengjie.agent.tool.input.ListMealPlansInput;
import me.zhengjie.agent.tool.input.ListRefundsInput;
import me.zhengjie.agent.tool.input.ListScheduledDishesInput;
import me.zhengjie.agent.tool.input.ListVerificationsInput;
import me.zhengjie.agent.tool.input.PreviewDishCandidatesInput;
import me.zhengjie.agent.tool.input.QueryBusinessMetricsInput;
import me.zhengjie.agent.tool.input.SearchCustomerProfilesInput;
import me.zhengjie.agent.tool.input.SearchDishesInput;
import me.zhengjie.agent.tool.input.SearchServiceCustomersInput;
import me.zhengjie.agent.tool.output.ToolOutputs;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 唯一工具元数据真相源。
 *
 * <p>名称、描述、输入类型、权限、数据分级、结果上限、超时和卡片类型只能在这里登记一次。
 * 模型可见工具由主系统签发的白名单再裁剪，未知名称不会降级为任意方法调用。</p>
 */
@Component
public class ToolRegistry {

    public static final String SEARCH_CUSTOMER_PROFILES = "searchCustomerProfiles";
    public static final String SEARCH_SERVICE_CUSTOMERS = "searchServiceCustomers";
    public static final String GET_SERVICE_CUSTOMER_DETAIL = "getServiceCustomerDetail";
    public static final String LIST_MEAL_PLANS = "listMealPlans";
    public static final String LIST_VERIFICATIONS = "listVerifications";
    public static final String LIST_REFUNDS = "listRefunds";
    public static final String PREVIEW_DISH_CANDIDATES = "previewDishCandidates";
    public static final String LIST_SCHEDULED_DISHES = "listScheduledDishes";
    public static final String SEARCH_DISHES = "searchDishes";
    public static final String GET_PACKAGE_DETAIL = "getPackageDetail";
    public static final String QUERY_BUSINESS_METRICS = "queryBusinessMetrics";
    public static final String EXPLAIN_BUSINESS_RULE = "explainBusinessRule";

    private final Map<String, ToolSpec<?>> specs;

    /** 构建并校验统一工具登记表。 */
    public ToolRegistry() {
        Map<String, ToolSpec<?>> values = new LinkedHashMap<>();
        register(values, new ToolSpec<>(SEARCH_CUSTOMER_PROFILES,
            "用途：查询客户档案，包括尚未下单客户。返回档案摘要，不提供订单成交/创建时间。可按 customerCode、customerId、customerName 或 hasOrder 筛选；不要用它回答下单时间、订单状态或排餐明细。可选字段未使用时省略或传 null，ID 必须为正整数且禁止传 0；page 从 1 开始，size 只能为 1-20；truncated=true 时才继续下一页。只读，单次最多返回 20 条，约 3 秒超时。",
            SearchCustomerProfilesInput.class, ToolOutputs.ToolResult.class,
            List.of("customerProfile:list"), 20, 3000, "CUSTOMER_PROFILE_LIST"));
        register(values, new ToolSpec<>(SEARCH_SERVICE_CUSTOMERS,
            "用途：查询以订单为根的服务客户，一笔订单返回一行。用户询问‘现在/当前/服务中的客户’或‘分别什么时候下单’时必须使用本工具并设置 status=ACTIVE，不要使用 searchCustomerProfiles。下单时间优先使用 dealTime，缺失时使用 createTime。status 只能为 ALL、ACTIVE、CANCELLED、COMPLETED、REFUNDED；可选 ID 必须为正整数，禁止传 0；日期只能为 yyyy-MM-dd 且不能传空字符串；page 从 1 开始，size 只能为 1-20，truncated=true 时才继续下一页。只读，单次最多返回 20 条，约 3 秒超时。",
            SearchServiceCustomersInput.class, ToolOutputs.ToolResult.class,
            List.of("customerOrder:list"), 20, 3000, "SERVICE_CUSTOMER_LIST"));
        register(values, new ToolSpec<>(GET_SERVICE_CUSTOMER_DETAIL,
            "用途：查询单个客户或订单的综合快照，包括客户档案、相关订单、套餐、餐数池和最近业务记录。customerId/customerCode/orderId/orderCode 至少提供一个，ID 必须为正整数；detailLevel 只能为 STANDARD 或 DIAGNOSTIC，默认 STANDARD。DIAGNOSTIC 仅在需要排查排餐问题时使用，查询较重且约 3 秒超时；只读，不支持分页或任意字段选择。",
            GetServiceCustomerDetailInput.class, ToolOutputs.ToolResult.class,
            List.of("customerProfile:list", "customerOrder:list"), 1, 3000, "SERVICE_CUSTOMER_DETAIL"));
        register(values, new ToolSpec<>(LIST_MEAL_PLANS,
            "用途：查询客户已经生成的实际排餐及其菜品明细，不是公共菜单或候选菜。客户/订单查询应提供 customerId/customerCode/orderId/orderCode 中至少一个；recordDate 只能单独使用，不能与 startDate/endDate 同时使用；日期必须为 yyyy-MM-dd；mealType 只能为 BREAKFAST、LUNCH、DINNER，查询全部餐次时省略该字段，不能传 ALL。page 从 1 开始，size 只能为 1-50；只读，单次最多返回 50 条，单条排餐菜品也可能被截断，约 3 秒超时。实时事实必须以本轮成功结果为准。",
            ListMealPlansInput.class, ToolOutputs.ToolResult.class,
            List.of("mealPlan:list"), 50, 3000, "MEAL_PLAN_LIST"));
        register(values, new ToolSpec<>(LIST_VERIFICATIONS,
            "用途：查询未删除核销记录及餐次。按客户或订单查询历史时可不带日期；不带客户/订单身份的广域查询必须同时提供 startDate/endDate，范围最多 31 天且 startDate 不得晚于 endDate。mealType 只能为 BREAKFAST、LUNCH、DINNER；page 从 1 开始，size 只能为 1-50。只读，返回不含金额，约 3 秒超时。",
            ListVerificationsInput.class, ToolOutputs.ToolResult.class,
            List.of("mealVerification:list"), 50, 3000, "VERIFICATION_LIST"));
        register(values, new ToolSpec<>(LIST_REFUNDS,
            "用途：查询退餐记录。按客户或订单查询历史时可不带日期；不带客户/订单身份的广域查询必须同时提供 startDate/endDate，范围最多 31 天且 startDate 不得晚于 endDate。page 从 1 开始，size 只能为 1-50。只读，返回不含退款金额，约 3 秒超时。",
            ListRefundsInput.class, ToolOutputs.ToolResult.class,
            List.of("mealRefund:list"), 50, 3000, "REFUND_LIST"));
        register(values, new ToolSpec<>(PREVIEW_DISH_CANDIDATES,
            "用途：查询客户指定日期午餐或晚餐的候选菜及套餐、过敏和忌口过滤原因；它不是已生成的实际餐单，也不能证明客户已经排餐。必须提供客户/订单身份、recordDate 和 mealType；mealType 只能为 LUNCH 或 DINNER，日期只能为 yyyy-MM-dd。只读，候选结果最多 20 条，约 3 秒超时。",
            PreviewDishCandidatesInput.class, ToolOutputs.ToolResult.class,
            List.of("customerProfile:list", "customerOrder:list", "package:list", "dish:list"), 20, 3000, "DISH_CANDIDATE_LIST"));
        register(values, new ToolSpec<>(LIST_SCHEDULED_DISHES,
            "用途：查询指定日期午餐/晚餐的公共排期菜单。必须提供 recordDate 和非空 mealTypes；mealTypes 只能包含 LUNCH、DINNER，日期只能为 yyyy-MM-dd。公共菜单不等于某个客户实际吃到的菜，不能替代 listMealPlans，也不能用于确认客户是否参与排餐。只读，约 3 秒超时。",
            ListScheduledDishesInput.class, ToolOutputs.ToolResult.class,
            List.of("mealPlan:list", "dish:list"), 20, 3000, "DISH_LIST"));
        register(values, new ToolSpec<>(SEARCH_DISHES,
            "用途：按菜名、受控菜品类型或启用状态分页查询菜品和配料摘要；不要提交任意字段、排序、SQL 或 URL。dishType 只能为 MAIN、SIDE、SOUP、VEGETABLE、RICE、RICE_TYPE；page 从 1 开始，size 只能为 1-20。只读，约 3 秒超时。",
            SearchDishesInput.class, ToolOutputs.ToolResult.class,
            List.of("dish:list"), 20, 3000, "DISH_LIST"));
        register(values, new ToolSpec<>(GET_PACKAGE_DETAIL,
            "用途：查询父套餐及其子套餐规格。必须提供 packageId 或 packageCode；ID 必须为正整数。只能查询登记的套餐信息，不返回价格、单价或金额；只读、单个结果、约 3 秒超时。",
            GetPackageDetailInput.class, ToolOutputs.ToolResult.class,
            List.of("package:list"), 5, 3000, "PACKAGE_DETAIL"));
        register(values, new ToolSpec<>(QUERY_BUSINESS_METRICS,
            "用途：查询已登记的业务统计数字。询问‘系统有多少核销数据/核销记录’时使用 VERIFICATION_RECORD_COUNT，它统计当前授权范围内全部未删除核销记录条数且不要求日期；询问某日有多少已核销客户时才使用 DAILY_VERIFIED_CUSTOMER_COUNT 并提供 recordDate，两者禁止混用。metric 只能使用登记的枚举，dimensions 只能使用 MEAL_TYPE、PACKAGE、CUSTOMER_SOURCE，最多 2 个；日期只能为 yyyy-MM-dd。需要当前系统数字时必须调用本工具，不能凭历史结果或估算回答。只读，单次最多返回 100 条，约 3 秒超时。",
            QueryBusinessMetricsInput.class, ToolOutputs.ToolResult.class,
            List.of("agentDiagnosis:list"), 100, 3000, "METRIC_RESULT"));
        register(values, new ToolSpec<>(EXPLAIN_BUSINESS_RULE,
            "用途：查询版本化、可追溯的业务规则说明。topic 只能为 MEAL_BALANCE、ORDER_EFFECTIVE、MEAL_PLAN_MATCH、DIETARY_FILTER、VERIFICATION_REFUND_EFFECT；不允许任意文档路径、SQL 或自由规则文本。只读、单个结果、约 3 秒超时。",
            ExplainBusinessRuleInput.class, ToolOutputs.ToolResult.class,
            List.of("agentDiagnosis:list"), 1, 3000, "BUSINESS_RULE"));
        this.specs = Collections.unmodifiableMap(values);
    }

    /** 返回全部登记工具的稳定顺序视图。 */
    public List<ToolSpec<?>> all() { return List.copyOf(specs.values()); }

    /** 根据主系统白名单裁剪本轮工具；null 只作为本地测试兼容含义，生产信封必须提供白名单。 */
    public List<ToolSpec<?>> visibleTo(Set<String> availableTools) {
        if (availableTools == null) return all();
        Set<String> normalized = new LinkedHashSet<>();
        for (String name : availableTools) if (name != null && !name.isBlank()) normalized.add(name.trim());
        List<ToolSpec<?>> result = new ArrayList<>();
        for (ToolSpec<?> spec : specs.values()) if (normalized.contains(spec.name())) result.add(spec);
        return List.copyOf(result);
    }

    /** 获取唯一工具定义，未知名称稳定失败。 */
    public ToolSpec<?> require(String name) {
        ToolSpec<?> spec = specs.get(name);
        if (spec == null) throw new IllegalArgumentException("TOOL_NOT_AVAILABLE: " + name);
        return spec;
    }

    /** 判断名称是否来自唯一登记表。 */
    public boolean contains(String name) { return name != null && specs.containsKey(name); }

    /** 校验工具元数据并防止同名工具覆盖已有登记。 */
    private void register(Map<String, ToolSpec<?>> target, ToolSpec<?> spec) {
        if (spec == null || spec.name() == null || spec.name().isBlank()
            || spec.inputType() == null || spec.outputType() == null
            || spec.requiredPermissions().isEmpty() || spec.maxResults() < 1
            || spec.timeoutMillis() < 100 || spec.cardType() == null || spec.cardType().isBlank()) {
            throw new IllegalStateException("Invalid Agent tool registration");
        }
        if (target.putIfAbsent(spec.name(), spec) != null) throw new IllegalStateException("Duplicate Agent tool: " + spec.name());
    }

    /** 工具的不可变元数据描述。 */
    public record ToolSpec<I>(String name, String description, Class<I> inputType,
                              Class<?> outputType, List<String> requiredPermissions,
                              int maxResults, int timeoutMillis, String cardType) {
        public ToolSpec {
        requiredPermissions = requiredPermissions == null ? List.of() : List.copyOf(requiredPermissions);
        }
    }
}
