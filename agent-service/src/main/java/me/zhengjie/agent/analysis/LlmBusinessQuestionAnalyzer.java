package me.zhengjie.agent.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 仅输出受控业务分析 JSON 的模型适配器。未知字段、SQL、URL 与工具名都会被拒绝。
 */
public class LlmBusinessQuestionAnalyzer implements BusinessQuestionAnalyzer {
    private static final Set<String> ROOT_FIELDS = Set.of("questionType", "domains", "entities", "filters", "metrics",
        "dimensions", "ambiguities", "confidence", "requiresClarification", "clarificationQuestion");
    private static final Set<String> ENTITY_FIELDS = Set.of("customerId", "customerCode", "customerName", "orderId", "orderCode",
        "mealPlanRecordId", "packageId", "dishId");
    private static final Set<String> FILTER_FIELDS = Set.of("recordDate", "startDate", "endDate", "mealType", "orderStatus",
        "page", "size", "recentLimit");
    private static final Set<String> AMBIGUITY_FIELDS = Set.of("field", "options", "material");
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmBusinessQuestionAnalyzer(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context) {
        try {
            String content = chatClient.prompt().user(prompt(question, context)).call().content();
            JsonNode root = objectMapper.readTree(normalizeJson(content));
            if (!isSafePayload(root)) return null;
            BusinessQuestionAnalysis analysis = objectMapper.treeToValue(root, BusinessQuestionAnalysis.class);
            if (analysis == null || analysis.getQuestionType() == null || analysis.getConfidence() < 0D || analysis.getConfidence() > 1D) return null;
            analysis.setSource("LLM");
            return analysis;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String prompt(String question, DiagnosisSlots context) throws Exception {
        return "你是后台智能客服的业务问题分析器，只输出 JSON。禁止查询、回答、SQL、URL、表名、字段名和工具名。"
            + "JSON 根字段只能为 questionType,domains,entities,filters,metrics,dimensions,ambiguities,confidence,requiresClarification,clarificationQuestion。"
            + "domains 只能使用 CUSTOMER,ORDER,MEAL_PLAN,VERIFICATION,REFUND,PACKAGE,DISH,BUSINESS_RULE,OPERATION_STATISTICS,NATURAL_LANGUAGE_REPORT；"
            + "metrics 和 dimensions 只能使用系统枚举。关键歧义必须 requiresClarification=true。"
            + "用户问题：" + (question == null ? "" : question) + "；受控上下文：" + objectMapper.writeValueAsString(context);
    }

    /**
     * 校验模型 JSON 的根字段和嵌套对象字段；未知字段不能依赖 ObjectMapper 默认行为被忽略。
     *
     * @param root 模型返回的 JSON 根节点
     * @return 满足受控 schema 且不包含 SQL/URL 文本时返回 true
     */
    static boolean isSafePayload(JsonNode root) {
        if (root == null || !root.isObject() || hasUnknownField(root, ROOT_FIELDS) || containsForbiddenText(root)) return false;
        if (!safeScalarObject(root.get("entities"), ENTITY_FIELDS)) return false;
        if (!safeScalarObject(root.get("filters"), FILTER_FIELDS)) return false;
        JsonNode ambiguities = root.get("ambiguities");
        if (ambiguities != null && (!ambiguities.isArray() || !safeAmbiguities(ambiguities))) return false;
        return safeEnumArray(root.get("domains")) && safeEnumArray(root.get("metrics")) && safeEnumArray(root.get("dimensions"));
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
}
