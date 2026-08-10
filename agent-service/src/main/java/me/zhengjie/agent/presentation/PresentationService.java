package me.zhengjie.agent.presentation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 为安全业务卡片生成受控展示描述。
 *
 * <p>已知卡片只读取 {@link PresentationRegistry}；未知卡片才使用无工具规划器。该服务不查询业务数据，
 * 也不把卡片值复制进展示描述。</p>
 */
public class PresentationService {
    public static final String FALLBACK_WARNING = "PRESENTATION_FALLBACK_APPLIED";
    private static final Logger log = LoggerFactory.getLogger(PresentationService.class);
    private static final Map<String, String> METRIC_TITLES = metricTitles();
    private final PresentationRegistry registry;
    private final CardSchemaInspector schemaInspector;
    private final PresentationPlanner planner;
    private final PresentationSuggestionValidator suggestionValidator;
    private final GenericPresentationFactory genericFactory;

    /** 使用系统展示注册表创建展示服务。 */
    public PresentationService(PresentationRegistry registry) {
        this(registry, new CardSchemaInspector(), null, new PresentationSuggestionValidator(),
            new GenericPresentationFactory());
    }

    /** 创建带未知卡片规划器的展示服务；已知规则仍完全绕过规划器。 */
    public PresentationService(PresentationRegistry registry, PresentationPlanner planner,
                                CardSchemaInspector schemaInspector,
                                PresentationSuggestionValidator suggestionValidator,
                                GenericPresentationFactory genericFactory) {
        this(registry, schemaInspector, planner, suggestionValidator, genericFactory);
    }

    /** 创建展示服务的完整依赖入口，供 Spring 和隔离测试使用。 */
    public PresentationService(PresentationRegistry registry, CardSchemaInspector schemaInspector,
                                PresentationPlanner planner, PresentationSuggestionValidator suggestionValidator,
                                GenericPresentationFactory genericFactory) {
        if (registry == null) throw new IllegalArgumentException("PRESENTATION_REGISTRY_REQUIRED");
        this.registry = registry;
        this.schemaInspector = schemaInspector == null ? new CardSchemaInspector() : schemaInspector;
        this.planner = planner;
        this.suggestionValidator = suggestionValidator == null ? new PresentationSuggestionValidator() : suggestionValidator;
        this.genericFactory = genericFactory == null ? new GenericPresentationFactory(this.suggestionValidator) : genericFactory;
    }

    /**
     * 为一个成功工具事实生成至多一个展示描述。
     *
     * @param sourceToolCallId 成功工具事实的唯一调用 ID
     * @param toolName 产生卡片的工具名，用于同卡片多路径分支
     * @param cardType 安全卡片类型
     * @param safeCardData 已执行内部标识隐藏后的卡片数据
     * @return 展示描述及与业务 partial 分离的展示告警
     */
    public PresentationResult present(String sourceToolCallId, String toolName, String cardType, JsonNode safeCardData) {
        PresentationRule rule = registry.find(cardType);
        if (rule == null) return presentUnknown(sourceToolCallId, cardType, safeCardData);
        PresentationRule.Template template = rule.templateFor(toolName);
        if (template == null) return presentUnknown(sourceToolCallId, cardType, safeCardData);
        if ("METRIC_RESULT".equals(cardType)) {
            template = metricTemplate(template, safeCardData)
                .withTitle(metricTitle(safeCardData));
        }
        PresentationDescriptor descriptor = new PresentationDescriptor(
            "v1", sourceToolCallId, cardType, PresentationDescriptor.DecisionSource.SYSTEM,
            template.title(), template.layout(), template.defaultView(), template.availableViews(),
            template.summary(), template.table(), template.chart());
        return new PresentationResult(descriptor, List.of());
    }

    /** 为未登记卡片执行独立规划；任何规划或校验异常都隔离在当前卡片并安全降级。 */
    private PresentationResult presentUnknown(String sourceToolCallId, String cardType, JsonNode safeCardData) {
        long startedAt = System.nanoTime();
        CardSchemaInspector.SchemaSummary schema = schemaInspector.inspect(cardType, safeCardData);
        String candidateView = "NONE";
        String reason = planner == null ? "PRESENTATION_PLANNER_UNAVAILABLE" : "PRESENTATION_FALLBACK_REQUIRED";
        try {
            if (planner == null) throw new IllegalStateException(reason);
            PresentationSuggestion suggestion = planner.plan(cardType, schema);
            candidateView = logView(suggestion == null ? null : suggestion.view());
            PresentationSuggestionValidator.ValidationResult validation = suggestionValidator.validate(
                suggestion, schema, safeCardData);
            if (!validation.valid()) {
                reason = validation.reason();
                logPresentation(cardType, candidateView, "REJECTED", reason, startedAt);
                return fallback(sourceToolCallId, cardType, safeCardData, schema);
            }
            PresentationDescriptor descriptor = genericFactory.createFromSuggestion(sourceToolCallId, cardType, validation);
            logPresentation(cardType, logView(validation.suggestion().view()), "PASSED", "PRESENTATION_VALID", startedAt);
            return new PresentationResult(descriptor, List.of());
        } catch (RuntimeException exception) {
            reason = stableReason(exception);
            logPresentation(cardType, candidateView, "FALLBACK", reason, startedAt);
            return fallback(sourceToolCallId, cardType, safeCardData, schema);
        }
    }

    /** 创建通用降级描述，并保持旧的缺少规则告警与新的稳定降级告警兼容。 */
    private PresentationResult fallback(String sourceToolCallId, String cardType, JsonNode safeCardData,
                                        CardSchemaInspector.SchemaSummary schema) {
        PresentationDescriptor descriptor = genericFactory.createFallback(sourceToolCallId, cardType, safeCardData, schema);
        return new PresentationResult(descriptor, List.of(PresentationRegistry.MISSING_RULE_WARNING, FALLBACK_WARNING));
    }

    /** 只记录展示元数据，不记录 prompt、卡片值或模型原文。 */
    private void logPresentation(String cardType, String view, String validation, String reason, long startedAt) {
        log.info("PRESENTATION_PLAN requestId={} cardType={} view={} validation={} reasonCode={} costMs={}",
            MDC.get("requestId"), cardType, view, validation, reason, elapsedMs(startedAt));
    }

    private String logView(String view) {
        if (view == null) return "NONE";
        try { return PresentationDescriptor.View.valueOf(view.trim().toUpperCase(java.util.Locale.ROOT)).name(); }
        catch (RuntimeException exception) { return "INVALID"; }
    }

    /** 将模型、校验和结构异常归一化为不含下游原文的展示原因码。 */
    private String stableReason(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.matches("[A-Z][A-Z0-9_:-]+")) return message.split(":", 2)[0];
            current = current.getCause();
        }
        return "PRESENTATION_FALLBACK_REQUIRED";
    }

    private long elapsedMs(long startedAt) { return (System.nanoTime() - startedAt) / 1_000_000L; }

    /** 兼容只传 cardType 的调用方，使用该卡片默认系统规则。 */
    public PresentationResult present(String sourceToolCallId, String cardType, JsonNode safeCardData) {
        return present(sourceToolCallId, null, cardType, safeCardData);
    }

    /** 返回注册表，供健康摘要和只读契约测试使用。 */
    public PresentationRegistry registry() { return registry; }

    /** 指标只有完整 breakdown 时才暴露表格和柱状图视图。 */
    private PresentationRule.Template metricTemplate(PresentationRule.Template template, JsonNode cardData) {
        JsonNode breakdown = cardData == null ? null : cardData.path("data").path("breakdown");
        if (!isComplete(cardData) || breakdown == null || !breakdown.isArray() || breakdown.isEmpty()) {
            return template.withViews(PresentationDescriptor.View.TEXT, List.of(PresentationDescriptor.View.TEXT), null, null);
        }
        return template.withViews(PresentationDescriptor.View.TEXT,
            List.of(PresentationDescriptor.View.TEXT, PresentationDescriptor.View.TABLE, PresentationDescriptor.View.BAR),
            template.table(), template.chart());
    }

    /** 同时检查外层和 data 内层的截断/告警标记，避免从不完整数据绘图。 */
    private boolean isComplete(JsonNode cardData) {
        if (cardData == null || cardData.path("truncated").asBoolean(false)) return false;
        JsonNode data = cardData.path("data");
        if (data.path("truncated").asBoolean(false) || data.path("data").path("truncated").asBoolean(false)) return false;
        return !hasWarnings(cardData) && !hasWarnings(data);
    }

    private boolean hasWarnings(JsonNode node) {
        return node != null && node.has("warnings") && node.path("warnings").isArray()
            && !node.path("warnings").isEmpty();
    }

    /** 将内部指标枚举转换为客服可理解的固定中文标题，未知枚举使用安全通用标题。 */
    private String metricTitle(JsonNode cardData) {
        String metric = cardData == null ? "" : cardData.path("data").path("metric").asText("");
        return METRIC_TITLES.getOrDefault(metric, "业务统计");
    }

    /** 构建当前登记指标的唯一展示标题目录。 */
    private static Map<String, String> metricTitles() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("CUSTOMER_PROFILE_COUNT", "客户档案总数");
        values.put("ACTIVE_SERVICE_CUSTOMER_COUNT", "服务中客户数");
        values.put("ACTIVE_ORDER_COUNT", "进行中订单数");
        values.put("VERIFICATION_RECORD_COUNT", "核销记录总数");
        values.put("DAILY_SCHEDULED_CUSTOMER_COUNT", "当日已排餐客户数");
        values.put("DAILY_VERIFIED_CUSTOMER_COUNT", "当日已核销客户数");
        values.put("DAILY_UNVERIFIED_CUSTOMER_COUNT", "当日待核销客户数");
        values.put("DAILY_UNSCHEDULED_CUSTOMER_COUNT", "当日待排餐客户数");
        values.put("MEAL_PLAN_FAILURE_COUNT", "排餐失败数");
        values.put("EXPIRING_ORDER_COUNT", "即将到期订单数");
        return Map.copyOf(values);
    }

    /** 展示结果和展示告警分离，调用方不得用展示告警覆盖业务 partial。 */
    public record PresentationResult(PresentationDescriptor descriptor, List<String> warnings) {
        public PresentationResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
