package me.zhengjie.agent.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.analysis.domain.BusinessTemporalExpression;
import me.zhengjie.agent.analysis.domain.BusinessTemporalIntent;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.security.AgentAccessContextHolder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 仅输出受控业务分析 JSON 的模型适配器。未知字段、SQL、URL 与工具名都会被拒绝。
 */
public class LlmBusinessQuestionAnalyzer implements BusinessQuestionAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(LlmBusinessQuestionAnalyzer.class);
    private static final Set<String> ROOT_FIELDS = Set.of("questionType", "queryTarget", "interactionMode", "referenceTurn",
        "domains", "entities", "filters", "mealScope", "correction", "metrics", "dimensions", "ambiguities", "subjects", "relations", "requestedFacts", "operation", "groupBy",
        "temporal", "confidence", "requiresClarification", "clarificationQuestion");
    private static final Set<String> ENTITY_FIELDS = Set.of("customerId", "customerCode", "customerName", "orderId", "orderCode",
        "mealPlanRecordId", "packageId", "dishId");
    private static final Set<String> FILTER_FIELDS = Set.of("recordDate", "startDate", "endDate", "mealType", "orderStatus",
        "page", "size", "recentLimit");
    private static final Set<String> AMBIGUITY_FIELDS = Set.of("field", "options", "material");
    private static final Set<String> CORRECTION_FIELDS = Set.of("reason", "observations", "requiresReplan");
    private static final Set<String> TEMPORAL_FIELDS = Set.of("expression", "explicitDate", "explicitStartDate", "explicitEndDate");
    private static final Set<String> NON_EXECUTABLE_MODEL_FIELDS = Set.of("observations", "analysisContext");
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final BeanOutputConverter<BusinessQuestionAnalysis> outputConverter;
    private final DeepSeekChatOptions jsonResponseOptions;
    private final BusinessSemanticPromptRenderer semanticPromptRenderer;
    private final ThreadLocal<String> lastFailureReason = new ThreadLocal<>();

    public LlmBusinessQuestionAnalyzer(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this(builder, objectMapper, new BusinessSemanticPromptRenderer(new BusinessSemanticCatalog()));
    }

    public LlmBusinessQuestionAnalyzer(ChatClient.Builder builder, ObjectMapper objectMapper,
                                       BusinessSemanticPromptRenderer semanticPromptRenderer) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.outputConverter = new BeanOutputConverter<>(BusinessQuestionAnalysis.class, objectMapper);
        this.semanticPromptRenderer = semanticPromptRenderer;
        this.jsonResponseOptions = DeepSeekChatOptions.builder()
            .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
            .temperature(0D)
            .build();
    }

    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context) {
        return analyze(question, context, null);
    }

    /** {@inheritDoc} */
    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context,
                                            LastBusinessQueryContext lastBusinessQueryContext) {
        lastFailureReason.remove();
        try {
            String content = chatClient.prompt()
                .system(systemPrompt())
                .user(userPrompt(question, context, lastBusinessQueryContext))
                .options(jsonResponseOptions)
                .call()
                .content();
            JsonNode rawRoot = objectMapper.readTree(normalizeJson(content));
            if (containsForbiddenText(rawRoot)) {
                lastFailureReason.set("MODEL_INVALID");
                log.warn("业务语义模型结果被拒绝 reason=FORBIDDEN_TEXT rootFields={}", rootFieldNames(rawRoot));
                return null;
            }
            JsonNode root = stripNonExecutableFields(rawRoot);
            String schemaViolation = schemaViolation(root);
            if (schemaViolation != null) {
                lastFailureReason.set("MODEL_INVALID");
                log.warn("业务语义模型结果被拒绝 reason={} rootFields={} nestedFields={}", schemaViolation,
                    rootFieldNames(root), rejectedNestedFields(root, schemaViolation));
                return null;
            }
            BusinessQuestionAnalysis analysis = objectMapper.treeToValue(root, BusinessQuestionAnalysis.class);
            if (!validAnalysis(analysis)) {
                lastFailureReason.set("MODEL_INVALID");
                log.warn("业务语义模型结果被拒绝 reason=SEMANTIC_INVALID queryTarget={} interactionMode={} confidence={}",
                    analysis == null ? null : analysis.getQueryTarget(), analysis == null ? null : analysis.getInteractionMode(),
                    analysis == null ? null : analysis.getConfidence());
                return null;
            }
            analysis.setSource("LLM");
            analysis.setSemanticCatalogVersion(AgentMetricCatalog.VERSION);
            lastFailureReason.remove();
            log.info("业务语义模型分析完成 queryTarget={} interactionMode={} mealScope={} confidence={} requiresClarification={}",
                analysis.getQueryTarget(), analysis.getInteractionMode(), analysis.getMealScope(), analysis.getConfidence(), analysis.isRequiresClarification());
            return analysis;
        } catch (Exception exception) {
            lastFailureReason.set(isTimeout(exception) ? "MODEL_TIMEOUT" : "MODEL_UNAVAILABLE");
            log.warn("业务语义模型分析失败 errorType={} errorMessage={}", exception.getClass().getSimpleName(), exception.getMessage());
            return null;
        }
    }

    /** 返回当前线程最近一次模型分析失败的稳定原因，不包含异常原文。 */
    public String getLastFailureReason() { return lastFailureReason.get(); }

    private String systemPrompt() {
        return "你是内部客服业务问题分析器，只输出符合 JSON Schema 的语义分析，不查库、不回答用户。"
            + "不得输出 SQL、URL、表名、任意字段名或工具名。不得仅因菜单、订单、排餐等单一名词忽略整句和会话上下文。"
            + "涉及排餐、客户和菜品过敏事实的跨对象问题可使用 MEAL_PLAN_ALLERGY_ANALYSIS；必须结合整句和会话上下文判断 mealScope，"
            + "语义表示全天范围时使用 ALL_AVAILABLE，明确单一餐次时 mealScope 和 filters.mealType 使用对应受控值，无法可靠判断且会实质改变结果时再追问，"
            + "subjects 仅为 MEAL_PLAN,CUSTOMER,DISH，relations 仅为 MEAL_PLAN_CUSTOMER,MEAL_PLAN_DISH，"
            + "requestedFacts 仅为 CUSTOMER_CODE,DISH_NAME,ALLERGY_FILTERED,ALLERGY_REASONS，operation=FILTER_AND_GROUP，groupBy 仅为 CUSTOMER_CODE。"
            + "当用户针对明确客户、日期和餐次询问为什么没排上、排餐失败原因或要求排查时，queryTarget 使用 MEAL_PLAN_DIAGNOSIS；"
            + "该目标只表达诊断意图和受控实体、过滤条件，不输出工具名。"
            + "用户否定、质疑或指出上轮结果异常时 interactionMode 优先为 CORRECTION；CORRECTION 必须 referenceTurn=PREVIOUS_BUSINESS_QUERY，"
            + "correction.requiresReplan=true，且不能简单复制上轮语义。当天公共菜单未指定餐次时 mealScope=ALL_AVAILABLE，"
            + "明确餐次时使用 LUNCH 或 DINNER。关键歧义必须 requiresClarification=true；不得根据常识补造业务数据。"
            + "询问某客户是否曾经排过餐、有没有排过餐时使用 CUSTOMER_MEAL_PLAN；如果用户没有限定时间，保持 temporal=UNSPECIFIED 且不填写日期，表示全部历史，不要求澄清日期。"
            + "时间必须使用 temporal.expression 表达：现在/当前/目前/截至现在在每日指标中使用 CURRENT_DAY，昨天用 PREVIOUS_DAY，明天用 NEXT_DAY，本周用 CURRENT_WEEK。"
            + "相对时间不得填写具体日期；明确日期使用 EXPLICIT_DATE，明确范围使用 EXPLICIT_RANGE。"
            + "必须区分仍有餐数的活跃客户与当天应服务但未排餐客户，也必须区分已排餐与排了但未核销。";
    }

    /**
     * 使用 Spring AI 根据业务分析 DTO 生成的 JSON Schema 约束模型输出，并补充当前受控上下文。
     *
     * @param question 当前用户问题
     * @param context 已提取的确定性槽位
     * @param lastBusinessQueryContext 最近一次业务查询摘要
     * @return 仅供模型进行语义分析的用户提示词
     */
    private String userPrompt(String question, DiagnosisSlots context, LastBusinessQueryContext lastBusinessQueryContext) throws Exception {
        return "只返回一个 JSON 对象，不要使用 Markdown。JSON 必须符合以下由 Spring AI 根据 Java DTO 生成的 Schema："
            + outputConverter.getFormat()
            + semanticPromptRenderer.render(AgentAccessContextHolder.availableTools())
            + "跨客户范围查询的 entities 使用空对象{}，不得新增 scope、customerScope 或列表字段。"
            + "correction.observations 只能使用受控枚举文本，不得输出自由分析说明。"
            + "跨客户过敏分析的结构示例：{\"queryTarget\":\"MEAL_PLAN_ALLERGY_ANALYSIS\",\"entities\":{},\"subjects\":[\"MEAL_PLAN\",\"CUSTOMER\",\"DISH\"],\"relations\":[\"MEAL_PLAN_CUSTOMER\",\"MEAL_PLAN_DISH\"],\"requestedFacts\":[\"CUSTOMER_CODE\",\"DISH_NAME\",\"ALLERGY_FILTERED\",\"ALLERGY_REASONS\"],\"operation\":\"FILTER_AND_GROUP\",\"groupBy\":[\"CUSTOMER_CODE\"]}。"
            + "明确客户未排餐原因诊断的结构示例：{\"queryTarget\":\"MEAL_PLAN_DIAGNOSIS\",\"entities\":{\"customerCode\":\"B3303\"},\"filters\":{\"recordDate\":\"2026-07-13\",\"mealType\":\"LUNCH\"}}。"
            + "当前用户问题：" + (question == null ? "" : question)
            + "；当前受控槽位：" + objectMapper.writeValueAsString(context)
            + "；最近业务查询摘要：" + objectMapper.writeValueAsString(lastBusinessQueryContext);
    }

    /**
     * 校验模型 JSON 的根字段和嵌套对象字段；未知字段不能依赖 ObjectMapper 默认行为被忽略。
     *
     * @param root 模型返回的 JSON 根节点
     * @return 满足受控 schema 且不包含 SQL/URL 文本时返回 true
     */
    static boolean isSafePayload(JsonNode root) {
        return schemaViolation(root) == null;
    }

    /** 返回不包含字段值的稳定 Schema 拒绝码，便于定位模型协议偏差。 */
    private static String schemaViolation(JsonNode root) {
        if (root == null || !root.isObject()) return "ROOT_SHAPE";
        if (hasUnknownField(root, ROOT_FIELDS)) return "ROOT_FIELD_UNKNOWN";
        if (containsForbiddenText(root)) return "FORBIDDEN_TEXT";
        if (!safeScalarObject(root.get("entities"), ENTITY_FIELDS)) return "ENTITIES_SHAPE";
        if (!safeScalarObject(root.get("filters"), FILTER_FIELDS)) return "FILTERS_SHAPE";
        JsonNode ambiguities = root.get("ambiguities");
        if (ambiguities != null && !ambiguities.isNull() && (!ambiguities.isArray() || !safeAmbiguities(ambiguities))) return "AMBIGUITIES_SHAPE";
        JsonNode correction = root.get("correction");
        if (correction != null && !correction.isNull() && !safeCorrection(correction)) return "CORRECTION_SHAPE";
        if (!safeScalarObject(root.get("temporal"), TEMPORAL_FIELDS)) return "TEMPORAL_SHAPE";
        if (!safeEnumArray(root.get("domains"))) return "DOMAINS_SHAPE";
        if (!safeEnumArray(root.get("metrics"))) return "METRICS_SHAPE";
        if (!safeEnumArray(root.get("dimensions"))) return "DIMENSIONS_SHAPE";
        if (!safeEnumArray(root.get("subjects"))) return "SUBJECTS_SHAPE";
        if (!safeEnumArray(root.get("relations"))) return "RELATIONS_SHAPE";
        if (!safeEnumArray(root.get("requestedFacts"))) return "REQUESTED_FACTS_SHAPE";
        if (!safeEnumArray(root.get("groupBy"))) return "GROUP_BY_SHAPE";
        JsonNode operation = root.get("operation");
        return operation == null || operation.isNull() || operation.isTextual() ? null : "OPERATION_SHAPE";
    }

    /** 仅允许实体和过滤对象使用预先声明的标量字段。 */
    private static boolean safeScalarObject(JsonNode node, Set<String> fields) {
        if (node == null || node.isNull()) return true;
        if (!node.isObject() || hasUnknownField(node, fields)) return false;
        Iterator<JsonNode> values = node.elements();
        while (values.hasNext()) if (!values.next().isValueNode()) return false;
        return true;
    }

    /** 校验澄清项结构，禁止模型通过嵌套 JSON 携带自由指令。 */
    private static boolean safeAmbiguities(JsonNode ambiguities) {
        for (JsonNode ambiguity : ambiguities) {
            if (!ambiguity.isObject() || hasUnknownField(ambiguity, AMBIGUITY_FIELDS)) return false;
            JsonNode field = ambiguity.get("field");
            JsonNode material = ambiguity.get("material");
            JsonNode options = ambiguity.get("options");
            if (field != null && !field.isTextual() || material != null && !material.isBoolean()) return false;
            if (options != null && (!options.isArray() || !safeEnumArray(options))) return false;
        }
        return true;
    }

    /** 校验纠错对象，observations 仅能是受控枚举数组。 */
    private static boolean safeCorrection(JsonNode correction) {
        if (!correction.isObject() || hasUnknownField(correction, CORRECTION_FIELDS)) return false;
        JsonNode reason = correction.get("reason");
        JsonNode requiresReplan = correction.get("requiresReplan");
        JsonNode observations = correction.get("observations");
        return (reason == null || reason.isTextual()) && (requiresReplan == null || requiresReplan.isBoolean())
            && (observations == null || safeEnumArray(observations));
    }

    /** 枚举数组只允许字符串值，具体枚举合法性继续由反序列化和规划层校验。 */
    private static boolean safeEnumArray(JsonNode node) {
        if (node == null || node.isNull()) return true;
        if (!node.isArray()) return false;
        for (JsonNode item : node) if (!item.isTextual()) return false;
        return true;
    }

    private static boolean hasUnknownField(JsonNode node, Set<String> allowed) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) if (!allowed.contains(fields.next())) return true;
        return false;
    }

    /** 丢弃已登记且不参与 QueryPlan 的模型说明字段；未登记字段仍由严格 Schema 拒绝。 */
    static JsonNode stripNonExecutableFields(JsonNode root) {
        if (root == null || !root.isObject()) return root;
        com.fasterxml.jackson.databind.node.ObjectNode normalized = ((com.fasterxml.jackson.databind.node.ObjectNode) root).deepCopy();
        NON_EXECUTABLE_MODEL_FIELDS.forEach(normalized::remove);
        return normalized;
    }

    /** 仅记录模型返回的根字段名用于协议排障，不记录用户问题、实体值或过滤值。 */
    private static List<String> rootFieldNames(JsonNode root) {
        if (root == null || !root.isObject()) return List.of();
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        root.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    /** 仅返回发生形状错误的嵌套字段名，不记录实体或过滤条件值。 */
    private static List<String> rejectedNestedFields(JsonNode root, String violation) {
        if (root == null || !root.isObject()) return List.of();
        String field = "ENTITIES_SHAPE".equals(violation) ? "entities"
            : "FILTERS_SHAPE".equals(violation) ? "filters"
            : "CORRECTION_SHAPE".equals(violation) ? "correction" : null;
        JsonNode nested = field == null ? null : root.get(field);
        return rootFieldNames(nested);
    }

    private static boolean containsForbiddenText(JsonNode node) {
        if (node.isTextual()) {
            String value = node.asText().toLowerCase();
            return value.contains("select ") || value.contains("insert ") || value.contains("update ")
                || value.contains("delete ") || value.contains("http://") || value.contains("https://") || value.contains("/api/");
        }
        for (JsonNode child : node) if (containsForbiddenText(child)) return true;
        return false;
    }

    private String normalizeJson(String content) {
        if (content == null) return "";
        String normalized = content.trim();
        if (!normalized.startsWith("```")) return normalized;
        int firstLineEnd = normalized.indexOf('\n');
        int lastFence = normalized.lastIndexOf("```");
        return firstLineEnd >= 0 && lastFence > firstLineEnd ? normalized.substring(firstLineEnd + 1, lastFence).trim() : normalized;
    }

    /** 校验纠错协议的必填关系，拒绝模型仅复制上轮查询的伪纠错。 */
    private boolean validAnalysis(BusinessQuestionAnalysis analysis) {
        if (analysis == null || analysis.getQuestionType() == null || analysis.getConfidence() < 0D || analysis.getConfidence() > 1D) return false;
        if (!validTemporal(analysis.getTemporal()) || !validMetricDomains(analysis)) return false;
        if (analysis.getInteractionMode() == me.zhengjie.agent.analysis.domain.BusinessInteractionMode.CORRECTION) {
            return "PREVIOUS_BUSINESS_QUERY".equals(analysis.getReferenceTurn()) && analysis.getCorrection() != null
                && analysis.getCorrection().isRequiresReplan();
        }
        return true;
    }

    /** 校验相对时间与显式日期字段互斥，具体日期仍由服务端 Resolver 解析。 */
    private boolean validTemporal(BusinessTemporalIntent temporal) {
        if (temporal == null || temporal.getExpression() == null) return true;
        boolean hasDate = notBlank(temporal.getExplicitDate());
        boolean hasStart = notBlank(temporal.getExplicitStartDate());
        boolean hasEnd = notBlank(temporal.getExplicitEndDate());
        if (temporal.getExpression() == BusinessTemporalExpression.EXPLICIT_DATE) return hasDate && !hasStart && !hasEnd;
        if (temporal.getExpression() == BusinessTemporalExpression.EXPLICIT_RANGE) return !hasDate && hasStart && hasEnd;
        return !hasDate && !hasStart && !hasEnd;
    }

    /** 指标必须登记在目录中，并且属于模型声明的业务领域。 */
    private boolean validMetricDomains(BusinessQuestionAnalysis analysis) {
        if (analysis.getMetrics() == null) return true;
        for (AgentQueryMetric metric : analysis.getMetrics()) {
            AgentMetricDefinition definition = AgentMetricCatalog.definition(metric);
            if (definition == null || analysis.getDomains() == null || !analysis.getDomains().contains(definition.getDomain())) return false;
        }
        return true;
    }

    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }

    private boolean isTimeout(Exception exception) {
        String name = exception.getClass().getSimpleName().toLowerCase();
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        return name.contains("timeout") || message.contains("timeout") || message.contains("timed out");
    }
}
