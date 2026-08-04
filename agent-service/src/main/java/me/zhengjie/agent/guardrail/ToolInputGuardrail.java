package me.zhengjie.agent.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.tool.ToolRegistry;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/** 工具调用前的 Schema、枚举、日期、分页和查询范围护栏。 */
public class ToolInputGuardrail {
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
        "permission", "permissions", "internaltoken", "token", "authorization", "datascope", "departmentids",
        "fields", "select", "sort", "orderby", "sql", "url", "endpoint", "table", "column");

    private final ObjectMapper objectMapper;
    private final SensitiveDataPolicy sensitiveDataPolicy;

    /** 创建工具输入护栏并复用统一敏感数据策略。 */
    public ToolInputGuardrail(ObjectMapper objectMapper, SensitiveDataPolicy sensitiveDataPolicy) {
        this.objectMapper = objectMapper;
        this.sensitiveDataPolicy = sensitiveDataPolicy;
    }

    /** 校验原始 JSON，并在成功后返回强类型输入对象。 */
    public <I> I validate(ToolRegistry.ToolSpec<I> spec, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) throw rejected("TOOL_INPUT_INVALID", "tool input is required");
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (node == null || !node.isObject()) throw rejected("TOOL_INPUT_INVALID", "tool input must be an object");
            rejectForbiddenFields(node);
            I value = objectMapper.readerFor(spec.inputType())
                .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(rawJson);
            validateCommon(spec, node);
            return value;
        } catch (ToolGuardrailException exception) {
            throw exception;
        } catch (Exception exception) {
            throw rejected("TOOL_INPUT_INVALID", "tool input does not match schema");
        }
    }

    /** 直接校验已反序列化输入，供非 Spring AI 测试和工具适配器复用。 */
    public void validateObject(ToolRegistry.ToolSpec<?> spec, Object input) {
        if (input == null) throw rejected("TOOL_INPUT_INVALID", "tool input is required");
        validateCommon(spec, objectMapper.valueToTree(input));
    }

    /** 拒绝权限、Token、SQL、动态 URL 等不属于工具 Schema 的字段。 */
    private void rejectForbiddenFields(JsonNode node) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String field = names.next().toLowerCase(Locale.ROOT);
            if (FORBIDDEN_FIELDS.contains(field)) throw rejected("TOOL_INPUT_FORBIDDEN_FIELD", "tool input contains forbidden field");
        }
    }

    /** 校验工具共用的敏感字段、分页、日期范围和业务必填条件。 */
    private void validateCommon(ToolRegistry.ToolSpec<?> spec, JsonNode node) {
        String toolName = spec.name();
        sensitiveDataPolicy.assertSafe(node);
        validatePage(node, spec.maxResults());
        validateDates(node);
        validateRequiredFields(toolName, node);
        if ((ToolRegistry.LIST_VERIFICATIONS.equals(toolName) || ToolRegistry.LIST_REFUNDS.equals(toolName))
            && !hasIdentity(node) && (!hasText(node, "startDate") || !hasText(node, "endDate"))) {
            throw rejected("TOOL_DATE_RANGE_REQUIRED", "broad history query requires a date range");
        }
        if ((ToolRegistry.LIST_VERIFICATIONS.equals(toolName) || ToolRegistry.LIST_REFUNDS.equals(toolName))
            && hasText(node, "startDate") && hasText(node, "endDate")) {
            LocalDate start = parseDate(node.get("startDate").asText());
            LocalDate end = parseDate(node.get("endDate").asText());
            if (end.isBefore(start) || end.isAfter(start.plusDays(30))) throw rejected("TOOL_DATE_RANGE_INVALID", "history date range is limited to 31 days");
        }
        if (ToolRegistry.QUERY_BUSINESS_METRICS.equals(toolName)) {
            JsonNode dimensions = node.get("dimensions");
            if (dimensions != null && dimensions.isArray() && dimensions.size() > 2) {
                throw rejected("TOOL_ENUM_INVALID", "at most two metric dimensions are allowed");
            }
        }
    }

    /** 校验分页参数为正数且不超过工具注册的结果上限。 */
    private void validatePage(JsonNode node, int maxResults) {
        for (String field : new String[]{"page", "size"}) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) continue;
            if (!value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > maxResults) {
                throw rejected("TOOL_PAGINATION_INVALID", "pagination is outside the allowed range");
            }
        }
    }

    /** 校验工具 Schema 无法仅靠 JavaBean 类型表达的必填业务条件。 */
    private void validateRequiredFields(String toolName, JsonNode node) {
        if (ToolRegistry.GET_SERVICE_CUSTOMER_DETAIL.equals(toolName)
            && !hasIdentity(node)) {
            throw rejected("TOOL_INPUT_INVALID", "customer or order identity is required");
        }
        if (ToolRegistry.PREVIEW_DISH_CANDIDATES.equals(toolName)
            && (!hasIdentity(node) || !hasText(node, "recordDate") || !hasText(node, "mealType"))) {
            throw rejected("TOOL_INPUT_INVALID", "customer, date and meal type are required");
        }
        if (ToolRegistry.LIST_SCHEDULED_DISHES.equals(toolName)
            && (!hasText(node, "recordDate") || !node.has("mealTypes")
                || !node.get("mealTypes").isArray() || node.get("mealTypes").isEmpty())) {
            throw rejected("TOOL_INPUT_INVALID", "date and meal types are required");
        }
        if (ToolRegistry.GET_PACKAGE_DETAIL.equals(toolName)
            && !hasValue(node, "packageId") && !hasText(node, "packageCode")) {
            throw rejected("TOOL_INPUT_INVALID", "package identity is required");
        }
        if (ToolRegistry.QUERY_BUSINESS_METRICS.equals(toolName)
            && (!hasText(node, "metric") || !node.get("metric").isTextual())) {
            throw rejected("TOOL_INPUT_INVALID", "metric is required");
        }
        if (ToolRegistry.EXPLAIN_BUSINESS_RULE.equals(toolName)
            && (!hasText(node, "topic") || !node.get("topic").isTextual())) {
            throw rejected("TOOL_INPUT_INVALID", "rule topic is required");
        }
        if (ToolRegistry.PREVIEW_DISH_CANDIDATES.equals(toolName)
            && "BREAKFAST".equalsIgnoreCase(node.path("mealType").asText())) {
            throw rejected("TOOL_ENUM_INVALID", "candidate preview supports lunch or dinner only");
        }
        if (ToolRegistry.LIST_SCHEDULED_DISHES.equals(toolName)) {
            for (JsonNode mealType : node.get("mealTypes")) {
                if (!"LUNCH".equalsIgnoreCase(mealType.asText())
                    && !"DINNER".equalsIgnoreCase(mealType.asText())) {
                    throw rejected("TOOL_ENUM_INVALID", "scheduled menu supports lunch or dinner only");
                }
            }
        }
    }

    /** 识别并校验请求中出现的日期和时间字符串。 */
    private void validateDates(JsonNode node) {
        Iterator<JsonNode> values = node.elements();
        while (values.hasNext()) {
            JsonNode value = values.next();
            if (!value.isTextual()) continue;
            String text = value.asText();
            if (text.matches("\\d{4}-\\d{2}-\\d{2}")) parseDate(text);
            else if (text.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
                try { java.time.LocalDateTime.parse(text.replace("Z", "")); }
                catch (DateTimeParseException exception) { throw rejected("TOOL_DATE_INVALID", "date must use yyyy-MM-dd"); }
            }
        }
    }

    /** 将 yyyy-MM-dd 文本解析为日期，失败时返回稳定护栏错误。 */
    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { throw rejected("TOOL_DATE_INVALID", "date must use yyyy-MM-dd"); }
    }

    /** 判断请求是否携带客户或订单关联身份。 */
    private boolean hasIdentity(JsonNode node) {
        return hasValue(node, "customerId") || hasText(node, "customerCode")
            || hasValue(node, "orderId") || hasText(node, "orderCode");
    }

    /** 判断 JSON 字段是否存在且非 null。 */
    private boolean hasValue(JsonNode node, String name) { return node.has(name) && !node.get(name).isNull(); }
    /** 判断 JSON 字段是否为非空文本。 */
    private boolean hasText(JsonNode node, String name) { return hasValue(node, name) && node.get(name).isTextual() && !node.get(name).asText().isBlank(); }
    /** 构造统一工具输入拒绝异常。 */
    private ToolGuardrailException rejected(String code, String message) { return new ToolGuardrailException(code, message); }
}
