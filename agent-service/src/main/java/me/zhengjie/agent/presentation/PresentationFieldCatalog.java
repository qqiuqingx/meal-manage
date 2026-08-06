package me.zhengjie.agent.presentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 已知安全卡片的展示字段目录。
 *
 * <p>目录以卡片类型和数据路径为边界登记字段，展示规则只能引用这里的语义字段。
 * 内部 ID、手机号原文、地址、金额、Token、权限和 SQL 等字段不会进入目录。</p>
 */
public class PresentationFieldCatalog {
    private static final Set<String> FORBIDDEN_FIELD_TERMS = Set.of(
        "customerid", "orderid", "dishid", "packageid", "phone", "mobile", "address",
        "amount", "price", "money", "payment", "token", "secret", "password", "permission",
        "authorization", "sql", "jdbc", "url");

    private final Map<String, Map<String, Map<String, FieldDefinition>>> fields = new LinkedHashMap<>();

    /** 创建当前 v1 业务卡片的完整安全字段目录。 */
    public PresentationFieldCatalog() {
        registerList("CUSTOMER_PROFILE_LIST", "items",
            field("customerCode", Type.TEXT, Shape.SCALAR), field("customerName", Type.TEXT, Shape.SCALAR),
            field("hasOrder", Type.BOOLEAN, Shape.SCALAR), field("createTime", Type.DATE_TIME, Shape.SCALAR),
            // 仅允许主系统已脱敏的手机号摘要；手机号原文字段不在目录中。
            field("maskedPhone", Type.TEXT, Shape.SCALAR));
        registerList("SERVICE_CUSTOMER_LIST", "items",
            field("customerCode", Type.TEXT, Shape.SCALAR), field("customerName", Type.TEXT, Shape.SCALAR),
            field("orderCode", Type.TEXT, Shape.SCALAR), field("orderTime", Type.DATE_TIME, Shape.SCALAR),
            field("status", Type.STATUS, Shape.SCALAR), field("parentPackageName", Type.TEXT, Shape.SCALAR));

        registerList("SERVICE_CUSTOMER_DETAIL", "data.profile",
            field("customerCode", Type.TEXT, Shape.SCALAR), field("customerName", Type.TEXT, Shape.SCALAR),
            field("hasOrder", Type.BOOLEAN, Shape.SCALAR), field("createTime", Type.DATE_TIME, Shape.SCALAR),
            field("maskedPhone", Type.TEXT, Shape.SCALAR));
        registerList("SERVICE_CUSTOMER_DETAIL", "data");
        registerList("SERVICE_CUSTOMER_DETAIL", "data.orders", serviceCustomerFields());
        registerList("SERVICE_CUSTOMER_DETAIL", "data.mealPlans",
            field("recordDate", Type.DATE, Shape.SCALAR), field("mealType", Type.MEAL_TYPE, Shape.SCALAR),
            field("status", Type.STATUS, Shape.SCALAR), field("verified", Type.BOOLEAN, Shape.SCALAR),
            field("failureReason", Type.TEXT, Shape.SCALAR), field("dishes", Type.TEXT, Shape.ARRAY));
        registerList("SERVICE_CUSTOMER_DETAIL", "data.verifications", verificationFields());
        registerList("SERVICE_CUSTOMER_DETAIL", "data.refunds", refundFields());

        registerList("MEAL_PLAN_LIST", "items",
            field("recordDate", Type.DATE, Shape.SCALAR), field("mealType", Type.MEAL_TYPE, Shape.SCALAR),
            field("status", Type.STATUS, Shape.SCALAR), field("verified", Type.BOOLEAN, Shape.SCALAR),
            field("failureReason", Type.TEXT, Shape.SCALAR), field("dishes", Type.TEXT, Shape.ARRAY));
        registerList("VERIFICATION_LIST", "items", verificationFields());
        registerList("REFUND_LIST", "items", refundFields());

        registerList("DISH_LIST", "items",
            field("name", Type.TEXT, Shape.SCALAR), field("dishType", Type.TEXT, Shape.SCALAR),
            field("enabled", Type.BOOLEAN, Shape.SCALAR), field("ingredients", Type.TEXT, Shape.ARRAY));
        registerList("DISH_LIST", "data.groups[].items",
            field("dishName", Type.TEXT, Shape.SCALAR), field("dishTypeName", Type.TEXT, Shape.SCALAR),
            field("dishTypeCode", Type.TEXT, Shape.SCALAR), field("enabled", Type.BOOLEAN, Shape.SCALAR),
            field("ingredientNames", Type.TEXT, Shape.ARRAY), field("mealTypes", Type.MEAL_TYPE, Shape.ARRAY));

        registerList("DISH_CANDIDATE_LIST", "data.items",
            field("dishName", Type.TEXT, Shape.SCALAR), field("name", Type.TEXT, Shape.SCALAR),
            field("dishTypeCode", Type.TEXT, Shape.SCALAR), field("dishType", Type.TEXT, Shape.SCALAR),
            field("available", Type.BOOLEAN, Shape.SCALAR), field("filterReasons", Type.TEXT, Shape.ARRAY));
        registerList("DISH_CANDIDATE_LIST", "data",
            field("present", Type.BOOLEAN, Shape.SCALAR), field("customerCode", Type.TEXT, Shape.SCALAR),
            field("recordDate", Type.DATE, Shape.SCALAR), field("mealTypeCode", Type.MEAL_TYPE, Shape.SCALAR),
            field("totalCandidateCount", Type.NUMBER, Shape.SCALAR), field("availableCandidateCount", Type.NUMBER, Shape.SCALAR),
            field("filteredCandidateCount", Type.NUMBER, Shape.SCALAR));

        registerList("PACKAGE_DETAIL", "data",
            field("packageCode", Type.TEXT, Shape.SCALAR), field("packageName", Type.TEXT, Shape.SCALAR));
        registerList("PACKAGE_DETAIL", "data.subPackages",
            field("subPackageCode", Type.TEXT, Shape.SCALAR), field("subPackageName", Type.TEXT, Shape.SCALAR),
            field("meatCount", Type.NUMBER, Shape.SCALAR), field("vegCount", Type.NUMBER, Shape.SCALAR),
            field("includeSoup", Type.BOOLEAN, Shape.SCALAR), field("includeRice", Type.BOOLEAN, Shape.SCALAR),
            field("enabled", Type.BOOLEAN, Shape.SCALAR));

        registerList("METRIC_RESULT", "data",
            field("metric", Type.TEXT, Shape.SCALAR), field("total", Type.NUMBER, Shape.SCALAR),
            field("queriedAt", Type.DATE_TIME, Shape.SCALAR));
        registerList("METRIC_RESULT", "data.breakdown",
            chartField("label", Type.TEXT, Shape.SCALAR), chartField("value", Type.NUMBER, Shape.SCALAR));

        registerList("BUSINESS_RULE", "data",
            field("ruleId", Type.TEXT, Shape.SCALAR), field("version", Type.TEXT, Shape.SCALAR),
            field("title", Type.TEXT, Shape.SCALAR), field("content", Type.TEXT, Shape.SCALAR),
            field("effectiveFrom", Type.DATE_TIME, Shape.SCALAR), field("updatedAt", Type.DATE_TIME, Shape.SCALAR));
    }

    /** 返回所有已登记卡片类型，保持注册顺序且不可变。 */
    public Set<String> cardTypes() { return Collections.unmodifiableSet(new LinkedHashSet<>(fields.keySet())); }

    /** 返回指定卡片的所有安全数据路径。 */
    public Set<String> pathsFor(String cardType) {
        return fields.containsKey(cardType)
            ? Collections.unmodifiableSet(new LinkedHashSet<>(fields.get(cardType).keySet())) : Set.of();
    }

    /** 判断数据路径是否属于指定卡片的安全目录。 */
    public boolean hasPath(String cardType, String dataPath) {
        return fields.containsKey(cardType) && fields.get(cardType).containsKey(dataPath);
    }

    /** 查找数据路径下的展示字段定义；未知路径返回空。 */
    public Map<String, FieldDefinition> fieldsFor(String cardType, String dataPath) {
        Map<String, FieldDefinition> values = fields.getOrDefault(cardType, Map.of()).get(dataPath);
        return values == null ? Map.of() : Collections.unmodifiableMap(values);
    }

    /** 判断字段是否登记在指定卡片路径下。 */
    public boolean hasField(String cardType, String dataPath, String field) {
        return fieldsFor(cardType, dataPath).containsKey(field);
    }

    /** 获取字段的类型和形态，用于规则校验和后续受控渲染。 */
    public FieldDefinition requireField(String cardType, String dataPath, String field) {
        FieldDefinition definition = fieldsFor(cardType, dataPath).get(field);
        if (definition == null) throw new IllegalArgumentException("PRESENTATION_UNKNOWN_FIELD");
        return definition;
    }

    /** 判断字段是否允许被图表使用。 */
    public boolean chartAllowed(String cardType, String dataPath, String field) {
        FieldDefinition definition = fieldsFor(cardType, dataPath).get(field);
        return definition != null && definition.chartAllowed();
    }

    /** 字段定义；值只描述数据形态，不携带业务数据。 */
    public record FieldDefinition(String field, Type type, Shape shape, boolean chartAllowed) { }

    /** 展示字段的受控类型。 */
    public enum Type { TEXT, DATE, DATE_TIME, STATUS, MEAL_TYPE, NUMBER, BOOLEAN }

    /** 展示字段的数据形态。 */
    public enum Shape { SCALAR, OBJECT, ARRAY }

    private void registerList(String cardType, String dataPath, FieldDefinition... definitions) {
        if (cardType == null || dataPath == null || definitions == null) throw new IllegalArgumentException("PRESENTATION_CATALOG_INVALID");
        PresentationDescriptor.requirePath(dataPath, "catalog.dataPath");
        Map<String, FieldDefinition> target = fields.computeIfAbsent(cardType, ignored -> new LinkedHashMap<>())
            .computeIfAbsent(dataPath, ignored -> new LinkedHashMap<>());
        for (FieldDefinition definition : definitions) {
            if (definition == null || definition.field() == null || definition.type() == null || definition.shape() == null
                || forbidden(definition.field()) || target.putIfAbsent(definition.field(), definition) != null) {
                throw new IllegalArgumentException("PRESENTATION_CATALOG_FIELD_INVALID");
            }
        }
    }

    private static FieldDefinition[] serviceCustomerFields() {
        return new FieldDefinition[] {
            field("customerCode", Type.TEXT, Shape.SCALAR), field("customerName", Type.TEXT, Shape.SCALAR),
            field("orderCode", Type.TEXT, Shape.SCALAR), field("orderTime", Type.DATE_TIME, Shape.SCALAR),
            field("status", Type.STATUS, Shape.SCALAR), field("parentPackageName", Type.TEXT, Shape.SCALAR)
        };
    }

    private static FieldDefinition[] verificationFields() {
        return new FieldDefinition[] {
            field("recordDate", Type.DATE, Shape.SCALAR), field("mealType", Type.MEAL_TYPE, Shape.SCALAR),
            field("count", Type.NUMBER, Shape.SCALAR), field("refunded", Type.BOOLEAN, Shape.SCALAR),
            field("operateTime", Type.DATE_TIME, Shape.SCALAR)
        };
    }

    private static FieldDefinition[] refundFields() {
        return new FieldDefinition[] {
            field("breakfastCount", Type.NUMBER, Shape.SCALAR), field("lunchDinnerCount", Type.NUMBER, Shape.SCALAR),
            field("verifiedBreakfastCount", Type.NUMBER, Shape.SCALAR),
            field("verifiedLunchDinnerCount", Type.NUMBER, Shape.SCALAR),
            field("reason", Type.TEXT, Shape.SCALAR), field("operateTime", Type.DATE_TIME, Shape.SCALAR)
        };
    }

    private static FieldDefinition field(String name, Type type, Shape shape) {
        return new FieldDefinition(name, type, shape, false);
    }

    private static FieldDefinition chartField(String name, Type type, Shape shape) {
        return new FieldDefinition(name, type, shape, true);
    }

    private static boolean forbidden(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return FORBIDDEN_FIELD_TERMS.stream().anyMatch(normalized::contains)
            && !"maskedphone".equals(normalized);
    }
}
