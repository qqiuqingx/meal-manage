package me.zhengjie.agent.presentation;

import me.zhengjie.agent.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static me.zhengjie.agent.presentation.PresentationDescriptor.Field;
import static me.zhengjie.agent.presentation.PresentationDescriptor.Format;
import static me.zhengjie.agent.presentation.PresentationDescriptor.Section;
import static me.zhengjie.agent.presentation.PresentationDescriptor.View;
import static me.zhengjie.agent.presentation.PresentationRule.Template;

/**
 * Agent 已知卡片的系统展示规则注册表。
 *
 * <p>注册表只按 cardType 保留一条规则；DISH_LIST 的两个工具通过同一条规则的受控
 * tool-name variant 选择不同安全数据路径。</p>
 */
public class PresentationRegistry {
    public static final String MISSING_RULE_WARNING = "PRESENTATION_RULE_MISSING";

    private final Map<String, PresentationRule> rules;
    private final List<String> healthWarnings;
    private final int currentCardTypeCount;

    /** 使用当前工具目录和内置安全字段目录创建 v1 系统规则。 */
    public PresentationRegistry(ToolRegistry toolRegistry) {
        this(toolRegistry, new PresentationFieldCatalog(), null);
    }

    /** 使用指定目录和规则创建注册表，供契约测试验证启动校验。 */
    public PresentationRegistry(ToolRegistry toolRegistry, PresentationFieldCatalog catalog,
                                List<PresentationRule> configuredRules) {
        if (toolRegistry == null || catalog == null) throw new IllegalArgumentException("PRESENTATION_REGISTRY_INVALID");
        List<PresentationRule> values = configuredRules == null ? defaultRules() : configuredRules;
        new PresentationRuleValidator(catalog).validateAll(values, toolRegistry);
        Map<String, PresentationRule> target = new LinkedHashMap<>();
        for (PresentationRule rule : values) target.put(rule.cardType(), rule);
        this.rules = Collections.unmodifiableMap(target);

        Set<String> currentCardTypes = toolRegistry.all().stream()
            .map(ToolRegistry.ToolSpec::cardType).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        this.currentCardTypeCount = currentCardTypes.size();
        List<String> warnings = new ArrayList<>();
        for (String cardType : currentCardTypes) if (!rules.containsKey(cardType)) warnings.add(MISSING_RULE_WARNING);
        this.healthWarnings = List.copyOf(new LinkedHashSet<>(warnings));
    }

    /** 返回所有系统规则，保持 cardType 稳定顺序。 */
    public List<PresentationRule> all() { return List.copyOf(rules.values()); }

    /** 返回系统规则覆盖的 cardType 数量。 */
    public int ruleCount() { return rules.size(); }

    /** 返回构造时工具目录中的去重 cardType 数量。 */
    public int currentCardTypeCount() { return currentCardTypeCount; }

    /** 返回当前工具卡片类型数量，用于健康摘要。 */
    public int currentCardTypeCount(ToolRegistry toolRegistry) {
        return (int) toolRegistry.all().stream().map(ToolRegistry.ToolSpec::cardType).distinct().count();
    }

    /** 获取指定卡片规则；未知卡片由 Phase 03 继续处理。 */
    public PresentationRule find(String cardType) { return rules.get(cardType); }

    /** 获取指定卡片规则，未知卡片稳定失败。 */
    public PresentationRule require(String cardType) {
        PresentationRule rule = find(cardType);
        if (rule == null) throw new IllegalArgumentException("PRESENTATION_RULE_NOT_FOUND");
        return rule;
    }

    /** 返回不包含卡片字段或业务数据的稳定健康告警码。 */
    public List<String> healthWarnings() { return healthWarnings; }

    /** 构建 11 类当前已知卡片的系统规则。 */
    private List<PresentationRule> defaultRules() {
        List<PresentationRule> values = new ArrayList<>();
        values.add(new PresentationRule("CUSTOMER_PROFILE_LIST", Template.table("客户档案", "items", fields(
            field("customerCode", "客户编号", Format.TEXT), field("customerName", "姓名", Format.TEXT),
            field("hasOrder", "是否有订单", Format.BOOLEAN), field("createTime", "创建时间", Format.DATE_TIME),
            field("maskedPhone", "手机号摘要", Format.TEXT)))));
        values.add(new PresentationRule("SERVICE_CUSTOMER_LIST", Template.table("服务客户订单", "items", fields(
            field("customerCode", "客户编号", Format.TEXT), field("customerName", "姓名", Format.TEXT),
            field("orderCode", "订单编号", Format.TEXT), field("orderTime", "下单时间", Format.DATE_TIME),
            field("status", "订单状态", Format.STATUS), field("parentPackageName", "套餐", Format.TEXT)))));

        Template detail = new Template("服务客户详情", me.zhengjie.agent.presentation.PresentationDescriptor.Layout.TABS,
            View.TABLE, List.of(View.TABLE),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Summary("data.profile", fields(
                field("customerCode", "客户编号", Format.TEXT), field("customerName", "姓名", Format.TEXT),
                field("hasOrder", "是否有订单", Format.BOOLEAN), field("createTime", "创建时间", Format.DATE_TIME),
                field("maskedPhone", "手机号摘要", Format.TEXT))),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Table("data", List.of(), List.of(
                section("orders", "订单", "data.orders", fields(
                    field("customerCode", "客户编号", Format.TEXT), field("customerName", "姓名", Format.TEXT),
                    field("orderCode", "订单编号", Format.TEXT), field("orderTime", "下单时间", Format.DATE_TIME),
                    field("status", "订单状态", Format.STATUS), field("parentPackageName", "套餐", Format.TEXT))),
                section("mealPlans", "排餐", "data.mealPlans", fields(
                    field("recordDate", "日期", Format.DATE), field("mealType", "餐次", Format.MEAL_TYPE),
                    field("status", "状态", Format.STATUS), field("verified", "已核销", Format.BOOLEAN),
                    field("failureReason", "失败原因", Format.TEXT), field("dishes", "菜品摘要", Format.TEXT))),
                section("verifications", "核销", "data.verifications", fields(
                    field("recordDate", "日期", Format.DATE), field("mealType", "餐次", Format.MEAL_TYPE),
                    field("count", "数量", Format.NUMBER), field("refunded", "已退餐", Format.BOOLEAN),
                    field("operateTime", "操作时间", Format.DATE_TIME))),
                section("refunds", "退餐", "data.refunds", fields(
                    field("breakfastCount", "早餐数量", Format.NUMBER), field("lunchDinnerCount", "午晚餐数量", Format.NUMBER),
                    field("verifiedBreakfastCount", "已核销早餐", Format.NUMBER),
                    field("verifiedLunchDinnerCount", "已核销午晚餐", Format.NUMBER),
                    field("reason", "原因", Format.TEXT), field("operateTime", "操作时间", Format.DATE_TIME))))), null);
        values.add(new PresentationRule("SERVICE_CUSTOMER_DETAIL", detail));

        values.add(new PresentationRule("MEAL_PLAN_LIST", Template.table("排餐记录", "items", fields(
            field("recordDate", "日期", Format.DATE), field("mealType", "餐次", Format.MEAL_TYPE),
            field("status", "状态", Format.STATUS), field("verified", "已核销", Format.BOOLEAN),
            field("failureReason", "失败原因", Format.TEXT), field("dishes", "菜品摘要", Format.TEXT)))));
        values.add(new PresentationRule("VERIFICATION_LIST", Template.table("核销记录", "items", fields(
            field("recordDate", "日期", Format.DATE), field("mealType", "餐次", Format.MEAL_TYPE),
            field("count", "数量", Format.NUMBER), field("refunded", "已退餐", Format.BOOLEAN),
            field("operateTime", "操作时间", Format.DATE_TIME)))));
        values.add(new PresentationRule("REFUND_LIST", Template.table("退餐记录", "items", fields(
            field("breakfastCount", "早餐数量", Format.NUMBER), field("lunchDinnerCount", "午晚餐数量", Format.NUMBER),
            field("verifiedBreakfastCount", "已核销早餐", Format.NUMBER),
            field("verifiedLunchDinnerCount", "已核销午晚餐", Format.NUMBER),
            field("reason", "原因", Format.TEXT), field("operateTime", "操作时间", Format.DATE_TIME)))));

        Template dishSearch = Template.table("菜品列表", "items", fields(
            field("name", "菜品名称", Format.TEXT), field("dishType", "菜品类型", Format.TEXT),
            field("enabled", "启用状态", Format.BOOLEAN), field("ingredients", "配料摘要", Format.TEXT)));
        Template dishScheduled = Template.table("排期菜单", "data.groups[].items", fields(
            field("dishName", "菜品名称", Format.TEXT), field("dishTypeName", "菜品类型", Format.TEXT),
            field("enabled", "启用状态", Format.BOOLEAN), field("ingredientNames", "配料摘要", Format.TEXT),
            field("mealTypes", "适用餐次", Format.MEAL_TYPE)));
        values.add(new PresentationRule("DISH_LIST", dishSearch,
            Map.of(ToolRegistry.LIST_SCHEDULED_DISHES, dishScheduled, ToolRegistry.SEARCH_DISHES, dishSearch)));

        Template candidate = new Template("候选菜预览", me.zhengjie.agent.presentation.PresentationDescriptor.Layout.TABS,
            View.TABLE, List.of(View.TABLE),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Summary("data", fields(
                field("present", "客户存在", Format.BOOLEAN), field("customerCode", "客户编号", Format.TEXT),
                field("recordDate", "日期", Format.DATE), field("mealTypeCode", "餐次", Format.MEAL_TYPE),
                field("totalCandidateCount", "候选总数", Format.NUMBER),
                field("availableCandidateCount", "可用数量", Format.NUMBER),
                field("filteredCandidateCount", "过滤数量", Format.NUMBER))),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Table("data.items", fields(
                field("dishName", "候选菜品", Format.TEXT), field("dishTypeCode", "菜品类型", Format.TEXT),
                field("available", "可用", Format.BOOLEAN), field("filterReasons", "过滤原因", Format.TEXT)), List.of()), null);
        values.add(new PresentationRule("DISH_CANDIDATE_LIST", candidate));

        Template packageDetail = new Template("套餐详情", me.zhengjie.agent.presentation.PresentationDescriptor.Layout.TABS,
            View.TABLE, List.of(View.TABLE),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Summary("data", fields(
                field("packageCode", "套餐编号", Format.TEXT), field("packageName", "套餐名称", Format.TEXT))),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Table("data.subPackages", fields(
                field("subPackageCode", "子套餐编号", Format.TEXT), field("subPackageName", "子套餐名称", Format.TEXT),
                field("meatCount", "荤菜数", Format.NUMBER), field("vegCount", "素菜数", Format.NUMBER),
                field("includeSoup", "含汤", Format.BOOLEAN), field("includeRice", "含米饭", Format.BOOLEAN),
                field("enabled", "启用状态", Format.BOOLEAN)), List.of()), null);
        values.add(new PresentationRule("PACKAGE_DETAIL", packageDetail));

        Template metric = new Template("运营指标", me.zhengjie.agent.presentation.PresentationDescriptor.Layout.TABS,
            View.TEXT, List.of(View.TEXT, View.TABLE, View.BAR),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Summary("data", fields(
                field("metric", "统计项", Format.TEXT), field("total", "总数", Format.NUMBER),
                field("queriedAt", "查询时间", Format.DATE_TIME))),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Table("data.breakdown", fields(
                field("label", "分组", Format.TEXT), field("value", "数值", Format.NUMBER)), List.of()),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Chart(View.BAR, "data.breakdown", "label",
                List.of("value"), "分组", List.of("数值")));
        values.add(new PresentationRule("METRIC_RESULT", metric));

        values.add(new PresentationRule("BUSINESS_RULE", new Template("业务规则", me.zhengjie.agent.presentation.PresentationDescriptor.Layout.TABS,
            View.TEXT, List.of(View.TEXT),
            new me.zhengjie.agent.presentation.PresentationDescriptor.Summary("data", fields(
                field("ruleId", "规则编号", Format.TEXT), field("version", "版本", Format.TEXT),
                field("title", "标题", Format.TEXT), field("content", "规则内容", Format.TEXT),
                field("effectiveFrom", "生效时间", Format.DATE_TIME), field("updatedAt", "更新时间", Format.DATE_TIME))), null, null)));
        return List.copyOf(values);
    }

    private static List<Field> fields(Field... values) { return List.of(values); }

    private static Field field(String name, String label, me.zhengjie.agent.presentation.PresentationDescriptor.Format format) {
        return new Field(name, label, format);
    }

    private static Section section(String id, String title, String path, List<Field> columns) {
        return new Section(id, title, path, columns);
    }
}
