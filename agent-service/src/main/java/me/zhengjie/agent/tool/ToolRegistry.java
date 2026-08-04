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
            "查询客户档案，包括尚未下单客户。按客户编号、内部关联 ID、脱敏姓名条件或是否已下单筛选；不要用它查询订单状态或实时排餐。",
            SearchCustomerProfilesInput.class, ToolOutputs.ToolResult.class,
            List.of("customerProfile:list"), 20, 3000, "CUSTOMER_PROFILE_LIST"));
        register(values, new ToolSpec<>(SEARCH_SERVICE_CUSTOMERS,
            "查询以订单为根的服务客户。每笔订单单独返回，不要把同一客户的多笔订单合并；可按状态、下单日期、客户或订单编号筛选。",
            SearchServiceCustomersInput.class, ToolOutputs.ToolResult.class,
            List.of("customerOrder:list"), 20, 3000, "SERVICE_CUSTOMER_LIST"));
        register(values, new ToolSpec<>(GET_SERVICE_CUSTOMER_DETAIL,
            "查询单个客户或订单的综合快照，包括客户档案、所有相关订单、套餐、餐数池和最近业务记录。编号或关联 ID 至少提供一个。",
            GetServiceCustomerDetailInput.class, ToolOutputs.ToolResult.class,
            List.of("customerProfile:list", "customerOrder:list"), 1, 3000, "SERVICE_CUSTOMER_DETAIL"));
        register(values, new ToolSpec<>(LIST_MEAL_PLANS,
            "查询已生成排餐和菜品明细。客户/订单、单日或日期范围、餐次均为受控条件；实时业务事实不要依赖历史对话中的旧结果。",
            ListMealPlansInput.class, ToolOutputs.ToolResult.class,
            List.of("mealPlan:list"), 50, 3000, "MEAL_PLAN_LIST"));
        register(values, new ToolSpec<>(LIST_VERIFICATIONS,
            "查询未删除核销记录。具体客户或订单历史可不带日期；广域查询必须提供最多 31 天日期范围。不要把核销记录解释为金额。",
            ListVerificationsInput.class, ToolOutputs.ToolResult.class,
            List.of("mealVerification:list"), 50, 3000, "VERIFICATION_LIST"));
        register(values, new ToolSpec<>(LIST_REFUNDS,
            "查询退餐记录。具体客户或订单历史可不带日期；广域查询必须提供最多 31 天日期范围。返回不包含退款金额。",
            ListRefundsInput.class, ToolOutputs.ToolResult.class,
            List.of("mealRefund:list"), 50, 3000, "REFUND_LIST"));
        register(values, new ToolSpec<>(PREVIEW_DISH_CANDIDATES,
            "查询客户指定日期午餐或晚餐的候选菜及套餐、过敏和忌口过滤原因；它不是已生成实际餐单。",
            PreviewDishCandidatesInput.class, ToolOutputs.ToolResult.class,
            List.of("customerProfile:list", "customerOrder:list", "package:list", "dish:list"), 20, 3000, "DISH_CANDIDATE_LIST"));
        register(values, new ToolSpec<>(LIST_SCHEDULED_DISHES,
            "查询指定日期午餐/晚餐的公共排期菜单。公共菜单不等于某个客户实际吃到的菜，不要用它替代 listMealPlans。",
            ListScheduledDishesInput.class, ToolOutputs.ToolResult.class,
            List.of("mealPlan:list", "dish:list"), 20, 3000, "DISH_LIST"));
        register(values, new ToolSpec<>(SEARCH_DISHES,
            "按菜名、受控菜品类型或启用状态分页查询菜品和配料摘要；不要提交任意字段、排序或 SQL。",
            SearchDishesInput.class, ToolOutputs.ToolResult.class,
            List.of("dish:list"), 20, 3000, "DISH_LIST"));
        register(values, new ToolSpec<>(GET_PACKAGE_DETAIL,
            "查询父套餐及其子套餐规格。只能使用套餐编码或受控关联 ID，不返回价格、单价或金额。",
            GetPackageDetailInput.class, ToolOutputs.ToolResult.class,
            List.of("package:list"), 5, 3000, "PACKAGE_DETAIL"));
        register(values, new ToolSpec<>(QUERY_BUSINESS_METRICS,
            "查询已登记的运营指标。metric 和 dimensions 只能使用枚举；需要当前系统数字时必须调用工具。",
            QueryBusinessMetricsInput.class, ToolOutputs.ToolResult.class,
            List.of("agentDiagnosis:list"), 100, 3000, "METRIC_RESULT"));
        register(values, new ToolSpec<>(EXPLAIN_BUSINESS_RULE,
            "查询版本化、可追溯的业务规则说明。只允许登记主题，不允许任意文档路径或自由规则文本。",
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
