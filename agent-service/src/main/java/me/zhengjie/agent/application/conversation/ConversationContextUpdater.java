package me.zhengjie.agent.application.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 根据本轮成功工具调用的强类型输入，确定性合并可持久化的会话业务焦点。
 *
 * <p>该类只读取登记工具输入中的固定字段白名单，不读取工具输出或模型回答，
 * 因此不会把自由文本、列表行或自然语言推断写入会话上下文。</p>
 */
public final class ConversationContextUpdater {
    private static final Logger log = LoggerFactory.getLogger(ConversationContextUpdater.class);
    private static final DateTimeFormatter BUSINESS_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> MEAL_TYPES = Set.of("BREAKFAST", "LUNCH", "DINNER");
    private static final int DEFAULT_MAX_TOOL_NAMES = 6;

    private final ObjectMapper objectMapper;
    private final int maxSuccessfulToolNames;

    /**
     * 创建会话上下文合并器。
     *
     * @param objectMapper 用于读取工具输入 JSON 的映射器
     * @param maxSuccessfulToolNames 最近查询摘要允许保留的去重工具名数量
     */
    public ConversationContextUpdater(ObjectMapper objectMapper, int maxSuccessfulToolNames) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.maxSuccessfulToolNames = maxSuccessfulToolNames > 0 ? maxSuccessfulToolNames : DEFAULT_MAX_TOOL_NAMES;
    }

    /**
     * 创建使用默认工具名上限的会话上下文合并器。
     *
     * @param objectMapper 用于读取工具输入 JSON 的映射器
     */
    public ConversationContextUpdater(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_MAX_TOOL_NAMES);
    }

    /**
     * 合并本轮成功工具事实，并生成安全的最近查询摘要。
     *
     * @param contextSlots 主系统持久化的上一版业务焦点
     * @param previousSummary 主系统持久化的上一版最近查询摘要
     * @param facts 本轮按调用顺序记录的工具事实
     * @param queriedAt 本轮查询时间
     * @return 合并后的槽位和最近查询摘要；没有成功事实时保留上一版摘要
     */
    public ContextUpdate update(DiagnosisSlots contextSlots,
                                Map<String, Object> previousSummary,
                                List<ToolExecutionContext.ToolFact> facts,
                                String queriedAt) {
        DiagnosisSlots slots = copyBusinessSlots(contextSlots);
        List<ToolExecutionContext.ToolFact> safeFacts = facts == null ? Collections.emptyList() : facts;
        List<String> successfulToolNames = new ArrayList<>();
        String lastToolName = null;
        boolean hasSuccessfulFact = false;
        boolean partial = false;

        for (ToolExecutionContext.ToolFact fact : safeFacts) {
            if (fact == null) {
                continue;
            }
            partial = partial || factHasIncompleteResult(fact);
            if (!fact.success()) {
                continue;
            }
            hasSuccessfulFact = true;
            if (hasText(fact.toolName())) {
                lastToolName = fact.toolName().trim();
                if (!successfulToolNames.contains(lastToolName)
                    && successfulToolNames.size() < maxSuccessfulToolNames) {
                    successfulToolNames.add(lastToolName);
                }
            }
            JsonNode input = parseJson(fact.inputJson(), "AGENT_CONTEXT_INPUT_INVALID");
            if (input != null && input.isObject()) {
                applyInput(slots, input);
            }
        }

        if (!hasSuccessfulFact) {
            return new ContextUpdate(slots, copySummary(previousSummary));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        if (hasText(lastToolName)) {
            summary.put("lastToolName", lastToolName);
        }
        summary.put("successfulToolNames", successfulToolNames);
        summary.put("filters", filters(slots));
        if (hasText(queriedAt)) {
            summary.put("queriedAt", queriedAt);
        }
        summary.put("partial", partial);
        return new ContextUpdate(slots, summary);
    }

    /**
     * 复制上一版会话槽位，只保留允许跨轮恢复的安全业务焦点字段。
     *
     * @param source 主系统下发的上一版槽位
     * @return 不会修改 source 的业务焦点副本
     */
    private DiagnosisSlots copyBusinessSlots(DiagnosisSlots source) {
        DiagnosisSlots copy = new DiagnosisSlots();
        if (source == null) {
            return copy;
        }
        copy.setCustomerId(positive(source.getCustomerId()));
        copy.setCustomerCode(normalizeText(source.getCustomerCode()));
        copy.setOrderId(positive(source.getOrderId()));
        copy.setOrderCode(normalizeText(source.getOrderCode()));
        copy.setMealPlanRecordId(positive(source.getMealPlanRecordId()));
        copy.setRecordDate(normalizeText(source.getRecordDate()));
        copy.setStartDate(normalizeText(source.getStartDate()));
        copy.setEndDate(normalizeText(source.getEndDate()));
        copy.setMealType(normalizeMealType(source.getMealType()));
        return copy;
    }

    /**
     * 从一个成功工具输入中读取固定业务字段并更新槽位。
     *
     * @param slots 当前正在合并的槽位副本
     * @param input 工具输入 JSON 对象
     */
    private void applyInput(DiagnosisSlots slots, JsonNode input) {
        Long customerId = positive(input, "customerId");
        String customerCode = text(input, "customerCode");
        if (customerId != null || customerCode != null) {
            boolean customerChanged = isDifferentCustomer(slots, customerId, customerCode);
            if (customerChanged) {
                slots.setCustomerId(null);
                slots.setCustomerCode(null);
                slots.setOrderId(null);
                slots.setOrderCode(null);
                slots.setMealPlanRecordId(null);
            }
            if (customerId != null) {
                slots.setCustomerId(customerId);
            }
            if (customerCode != null) {
                slots.setCustomerCode(customerCode);
            }
        }

        Long orderId = positive(input, "orderId");
        String orderCode = text(input, "orderCode");
        if (orderId != null || orderCode != null) {
            boolean orderChanged = isDifferentOrder(slots, orderId, orderCode);
            if (orderChanged) {
                slots.setOrderId(null);
                slots.setOrderCode(null);
                slots.setMealPlanRecordId(null);
            }
            if (orderId != null) {
                slots.setOrderId(orderId);
            }
            if (orderCode != null) {
                slots.setOrderCode(orderCode);
            }
        }

        Long mealPlanRecordId = positive(input, "mealPlanRecordId");
        if (mealPlanRecordId != null) {
            slots.setMealPlanRecordId(mealPlanRecordId);
        }

        applyDateFilter(slots, input);

        String mealType = normalizeMealType(text(input, "mealType"));
        if (mealType != null) {
            slots.setMealType(mealType);
        }
    }

    /**
     * 应用单日或完整日期范围，并确保两种日期形态互斥。
     *
     * @param slots 当前槽位副本
     * @param input 工具输入 JSON 对象
     */
    private void applyDateFilter(DiagnosisSlots slots, JsonNode input) {
        String recordDate = validDate(text(input, "recordDate"));
        String startDate = validDate(text(input, "startDate"));
        String endDate = validDate(text(input, "endDate"));
        boolean hasRangeFields = text(input, "startDate") != null || text(input, "endDate") != null;

        if (recordDate != null && !hasRangeFields) {
            slots.setRecordDate(recordDate);
            slots.setStartDate(null);
            slots.setEndDate(null);
            return;
        }
        if (recordDate == null && startDate != null && endDate != null
            && !LocalDate.parse(startDate, BUSINESS_DATE_FORMATTER).isAfter(LocalDate.parse(endDate, BUSINESS_DATE_FORMATTER))) {
            slots.setRecordDate(null);
            slots.setStartDate(startDate);
            slots.setEndDate(endDate);
        }
    }

    /**
     * 判断工具输入是否明确切换客户。
     *
     * @param slots 当前槽位副本
     * @param customerId 输入中的客户 ID
     * @param customerCode 输入中的客户编号
     * @return 两个明确标识任一与当前焦点不一致时返回 true
     */
    private boolean isDifferentCustomer(DiagnosisSlots slots, Long customerId, String customerCode) {
        return customerId != null && !Objects.equals(customerId, slots.getCustomerId())
            || customerCode != null && !customerCode.equalsIgnoreCase(normalizeText(slots.getCustomerCode()));
    }

    /**
     * 判断工具输入是否明确切换订单。
     *
     * @param slots 当前槽位副本
     * @param orderId 输入中的订单 ID
     * @param orderCode 输入中的订单编号
     * @return 两个明确标识任一与当前焦点不一致时返回 true
     */
    private boolean isDifferentOrder(DiagnosisSlots slots, Long orderId, String orderCode) {
        return orderId != null && !Objects.equals(orderId, slots.getOrderId())
            || orderCode != null && !orderCode.equalsIgnoreCase(normalizeText(slots.getOrderCode()));
    }

    /**
     * 生成最近查询摘要使用的安全过滤条件。
     *
     * @param slots 已合并的安全槽位
     * @return 仅包含编号、日期和餐次的过滤条件
     */
    private Map<String, Object> filters(DiagnosisSlots slots) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (slots.getCustomerId() != null) filters.put("customerId", slots.getCustomerId());
        if (hasText(slots.getCustomerCode())) filters.put("customerCode", slots.getCustomerCode());
        if (slots.getOrderId() != null) filters.put("orderId", slots.getOrderId());
        if (hasText(slots.getOrderCode())) filters.put("orderCode", slots.getOrderCode());
        if (slots.getMealPlanRecordId() != null) filters.put("mealPlanRecordId", slots.getMealPlanRecordId());
        if (hasText(slots.getRecordDate())) {
            filters.put("recordDate", slots.getRecordDate());
        } else {
            if (hasText(slots.getStartDate())) filters.put("startDate", slots.getStartDate());
            if (hasText(slots.getEndDate())) filters.put("endDate", slots.getEndDate());
        }
        if (hasText(slots.getMealType())) filters.put("mealType", slots.getMealType());
        return filters;
    }

    /**
     * 判断工具事实是否表示查询不完整。
     *
     * @param fact 工具事实
     * @return 工具失败、截断、部分结果或输出无法解析时返回 true
     */
    private boolean factHasIncompleteResult(ToolExecutionContext.ToolFact fact) {
        if (!fact.success()) {
            return true;
        }
        JsonNode output = parseJson(fact.outputJson(), "AGENT_CONTEXT_OUTPUT_INVALID");
        if (output == null || output.isNull()) {
            return true;
        }
        return hasBooleanFlag(output, "partial") || hasBooleanFlag(output, "truncated") || hasTruncatedWarning(output);
    }

    /**
     * 递归读取受控结果中的完整性标志。
     *
     * @param node 工具输出 JSON 节点
     * @param fieldName 标志字段名
     * @return 任一节点明确为 true 时返回 true
     */
    private boolean hasBooleanFlag(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) return false;
        if (node.isObject()) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isBoolean() && value.asBoolean()) return true;
            java.util.Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                if (hasBooleanFlag(values.next(), fieldName)) return true;
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasBooleanFlag(child, fieldName)) return true;
            }
        }
        return false;
    }

    /**
     * 读取结果告警中的截断标志。
     *
     * @param node 工具输出 JSON 节点
     * @return 告警文本包含截断含义时返回 true
     */
    private boolean hasTruncatedWarning(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (node.isObject()) {
            JsonNode warnings = node.get("warnings");
            if (warnings != null && warnings.isArray()) {
                for (JsonNode warning : warnings) {
                    if (warning.isTextual() && warning.asText().toUpperCase(Locale.ROOT).contains("TRUNCAT")) return true;
                }
            }
            java.util.Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                if (hasTruncatedWarning(values.next())) return true;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasTruncatedWarning(child)) return true;
            }
        }
        return false;
    }

    /**
     * 解析 JSON 字符串；失败时只记录稳定日志码并跳过该事实。
     *
     * @param json 待解析 JSON
     * @param errorCode 解析失败时记录的稳定日志码
     * @return JSON 节点，解析失败返回 null
     */
    private JsonNode parseJson(String json, String errorCode) {
        if (!hasText(json)) {
            log.warn(errorCode);
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            log.warn(errorCode);
            return null;
        }
    }

    /** 读取固定白名单中的正整数 JSON 字段。 */
    private Long positive(JsonNode input, String fieldName) {
        if (input == null || !input.has(fieldName)) return null;
        JsonNode value = input.get(fieldName);
        if (value == null || !value.isIntegralNumber() || value.asLong() <= 0) return null;
        return value.asLong();
    }

    /** 读取固定白名单中的非空文本字段。 */
    private String text(JsonNode input, String fieldName) {
        if (input == null || !input.has(fieldName)) return null;
        JsonNode value = input.get(fieldName);
        return value != null && value.isTextual() ? normalizeText(value.asText()) : null;
    }

    /** 返回合法业务日期，非法日期不会覆盖已有焦点。 */
    private String validDate(String value) {
        if (!hasText(value)) return null;
        try {
            LocalDate.parse(value, BUSINESS_DATE_FORMATTER);
            return value;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** 返回允许的单一餐次枚举。 */
    private String normalizeMealType(String value) {
        if (!hasText(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return MEAL_TYPES.contains(normalized) ? normalized : null;
    }

    /** 归一化非空文本字段。 */
    private String normalizeText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    /** 保留正整数标识，拒绝零和负数。 */
    private Long positive(Long value) {
        return value != null && value > 0 ? value : null;
    }

    /** 判断文本是否非空。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 复制旧摘要的顶层键，避免后续调用修改主系统请求对象。 */
    private Map<String, Object> copySummary(Map<String, Object> summary) {
        return summary == null ? null : new LinkedHashMap<>(summary);
    }

    /** 上下文更新结果；只包含可由主系统持久化的安全字段。 */
    public record ContextUpdate(DiagnosisSlots slots, Map<String, Object> lastBusinessQueryContext) { }
}
