package me.zhengjie.agent.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.rule.DiagnosisRule;
import me.zhengjie.agent.rule.RuleRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验可选的结构化排餐诊断结果。
 *
 * <p>普通回答不经过该类；只有模型明确返回包含 summary/reasons 的结构化对象时，
 * 才校验规则版本、必需工具、证据字段、原因级别和写操作声称。</p>
 */
public class DiagnosisResultValidator {

    private static final Set<String> ALLOWED_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");
    private static final List<String> FORBIDDEN_WRITE_CLAIMS = List.of("已修复", "已修改数据库", "已创建客户", "已执行排餐");
    private final ObjectMapper objectMapper;

    /** 使用默认 JSON 映射器构建校验器。 */
    public DiagnosisResultValidator() {
        this(new ObjectMapper());
    }

    /** 使用指定 JSON 映射器构建校验器，便于复用 Agent 的序列化配置。 */
    public DiagnosisResultValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /** 仅校验结果自身的结构，不依赖规则目录或工具事实。 */
    public List<DiagnosisValidationError> validate(DiagnosisResponse response) {
        return validate(response, null, List.of());
    }

    /** 校验结果结构和规则引用；不提供工具事实时只会报告结构/规则引用错误。 */
    public List<DiagnosisValidationError> validate(DiagnosisResponse response, RuleRegistry ruleRegistry) {
        return validate(response, ruleRegistry, List.of());
    }

    /**
     * 校验结果是否可以作为已验证诊断展示。
     *
     * @param response 模型返回的结构化诊断
     * @param ruleRegistry 当前场景规则注册表
     * @param toolFacts 本轮工具事实；缓存命中事实同样必须为成功事实
     * @return 稳定校验错误列表，空列表表示通过
     */
    public List<DiagnosisValidationError> validate(DiagnosisResponse response, RuleRegistry ruleRegistry,
                                                    List<ToolExecutionContext.ToolFact> toolFacts) {
        List<DiagnosisValidationError> errors = new ArrayList<>();
        if (response == null) {
            errors.add(error("response", "RESPONSE_NULL", "response must not be null", null));
            return errors;
        }
        if (blank(response.getSummary())) {
            errors.add(error("summary", "SUMMARY_BLANK", "summary must not be blank", response.getSummary()));
        } else if (containsForbiddenClaim(response.getSummary())) {
            errors.add(error("summary", "SUMMARY_FORBIDDEN_CLAIM", "summary contains a forbidden write claim", response.getSummary()));
        }
        if (!ALLOWED_LEVELS.contains(level(response.getConfidence()))) {
            errors.add(error("confidence", "CONFIDENCE_INVALID", "confidence must be HIGH, MEDIUM or LOW", response.getConfidence()));
        }
        if (response.getReasons() == null || response.getReasons().isEmpty()) {
            errors.add(error("reasons", "REASONS_EMPTY", "reasons must not be empty", response.getReasons()));
        } else {
            for (int i = 0; i < response.getReasons().size(); i++) {
                errors.addAll(validateReason(response.getReasons().get(i), i));
            }
        }
        if (response.getNextActions() == null || response.getNextActions().isEmpty()) {
            errors.add(error("nextActions", "NEXT_ACTIONS_EMPTY", "nextActions must not be empty", response.getNextActions()));
        }
        if (response.isFallback() && blank(response.getFallbackReason())) {
            errors.add(error("fallbackReason", "FALLBACK_REASON_BLANK", "fallbackReason must be set when fallback=true", response.getFallbackReason()));
        }
        if (!response.isFallback()) {
            errors.addAll(validateRulesAndEvidence(response, ruleRegistry, toolFacts));
        }
        return errors;
    }

    /** 校验失败时返回不带业务推断的人工复核结果，避免把未验证内容继续下发。 */
    public DiagnosisResponse validateOrFallback(DiagnosisResponse response, RuleRegistry ruleRegistry,
                                                List<ToolExecutionContext.ToolFact> toolFacts) {
        List<DiagnosisValidationError> errors = validate(response, ruleRegistry, toolFacts);
        if (errors.isEmpty()) {
            if (response != null && ruleRegistry != null) response.setRuleVersionDigest(ruleRegistry.getVersionDigest());
            markSuggestions(response);
            return response;
        }
        DiagnosisResponse fallback = new DiagnosisResponse();
        fallback.setSummary("AI 诊断结果不可用，建议按固定清单人工核对。");
        fallback.setConfidence("LOW");
        fallback.setFallback(true);
        fallback.setFallbackReason("AI 诊断结果校验失败，需人工核对。");
        fallback.setRuleVersionDigest(ruleRegistry == null ? null : ruleRegistry.getVersionDigest());
        fallback.setReasons(List.of(fallbackReason()));
        fallback.setNextActions(List.of("核对客户档案", "核对订单有效性", "核对排餐记录", "核对候选菜配置"));
        markSuggestions(fallback);
        return fallback;
    }

    private List<DiagnosisValidationError> validateRulesAndEvidence(DiagnosisResponse response,
                                                                      RuleRegistry ruleRegistry,
                                                                      List<ToolExecutionContext.ToolFact> toolFacts) {
        List<DiagnosisValidationError> errors = new ArrayList<>();
        if (ruleRegistry == null || ruleRegistry.getRules() == null || ruleRegistry.getRules().isEmpty()) {
            return errors;
        }
        if (blank(response.getRuleVersionDigest())) {
            errors.add(error("ruleVersionDigest", "RULE_VERSION_DIGEST_BLANK", "rule version digest must be returned", null));
        } else if (!response.getRuleVersionDigest().equals(ruleRegistry.getVersionDigest())) {
            errors.add(error("ruleVersionDigest", "RULE_VERSION_MISMATCH", "rule version digest does not match registry", response.getRuleVersionDigest()));
        }
        Map<String, DiagnosisRule> rulesById = rulesById(ruleRegistry);
        Set<String> successfulTools = new HashSet<>();
        List<JsonNode> successfulOutputs = new ArrayList<>();
        if (toolFacts != null) {
            for (ToolExecutionContext.ToolFact fact : toolFacts) {
                if (fact == null || !fact.success()) continue;
                successfulTools.add(fact.toolName());
                try {
                    JsonNode output = objectMapper.readTree(fact.outputJson());
                    if (output != null) successfulOutputs.add(output);
                } catch (Exception ignored) {
                    // 工具输出护栏已经负责拦截非法 JSON；此处只记录为不可用证据。
                }
            }
        }
        boolean hasBusinessEvidence = false;
        for (int i = 0; i < response.getReasons().size(); i++) {
            DiagnosisReasonDto reason = response.getReasons().get(i);
            if (reason == null || reason.getRuleIds() == null || reason.getRuleIds().isEmpty()) continue;
            Set<String> allowedEvidenceFields = new HashSet<>();
            Set<String> requiredTools = new HashSet<>();
            for (String ruleId : reason.getRuleIds()) {
                DiagnosisRule rule = rulesById.get(ruleId);
                if (rule == null) {
                    errors.add(error("reasons[" + i + "].ruleIds", "RULE_ID_UNKNOWN", "ruleId must exist in registry", ruleId));
                    continue;
                }
                if (!blank(rule.getReasonCode()) && !rule.getReasonCode().equals(reason.getCode())) {
                    errors.add(error("reasons[" + i + "].code", "REASON_CODE_RULE_MISMATCH", "reason code must match rule reasonCode", reason.getCode()));
                }
                if (rule.getRequiredTools() != null) requiredTools.addAll(rule.getRequiredTools());
                if (rule.getEvidenceFields() != null) allowedEvidenceFields.addAll(rule.getEvidenceFields());
            }
            for (String requiredTool : requiredTools) {
                if (!successfulTools.contains(requiredTool)) {
                    errors.add(error("reasons[" + i + "].ruleIds", "REQUIRED_TOOL_NOT_CALLED", "all required tools must succeed before conclusion", requiredTool));
                }
            }
            if (reason.getEvidence() == null || reason.getEvidence().isEmpty()) continue;
            for (int evidenceIndex = 0; evidenceIndex < reason.getEvidence().size(); evidenceIndex++) {
                DiagnosisEvidenceDto evidence = reason.getEvidence().get(evidenceIndex);
                String label = evidence == null ? null : evidence.getLabel();
                String field = "reasons[" + i + "].evidence[" + evidenceIndex + "].label";
                if (blank(label)) {
                    errors.add(error(field, "EVIDENCE_LABEL_BLANK", "evidence label must not be blank", label));
                } else if (!allowedEvidenceFields.contains(label)) {
                    errors.add(error(field, "EVIDENCE_LABEL_NOT_ALLOWED", "evidence label must be defined by referenced rules", label));
                } else if (!existsInToolFacts(successfulOutputs, label)) {
                    errors.add(error(field, "EVIDENCE_NOT_IN_TOOL_FACT", "evidence label must exist in a successful tool fact", label));
                } else {
                    hasBusinessEvidence = true;
                }
            }
        }
        if (!hasBusinessEvidence) {
            errors.add(error("evidence", "BUSINESS_EVIDENCE_EMPTY", "diagnosis must contain rule-defined tool evidence", response.getReasons()));
        }
        return errors;
    }

    /** 校验单条诊断原因的级别、规则、证据和建议字段。 */
    private List<DiagnosisValidationError> validateReason(DiagnosisReasonDto reason, int index) {
        List<DiagnosisValidationError> errors = new ArrayList<>();
        String prefix = "reasons[" + index + "]";
        if (reason == null) {
            errors.add(error(prefix, "REASON_NULL", "reason must not be null", null));
            return errors;
        }
        if (blank(reason.getCode())) errors.add(error(prefix + ".code", "REASON_CODE_BLANK", "reason code must not be blank", null));
        if (!ALLOWED_LEVELS.contains(level(reason.getLevel()))) errors.add(error(prefix + ".level", "REASON_LEVEL_INVALID", "reason level is invalid", reason.getLevel()));
        if (!ALLOWED_LEVELS.contains(level(reason.getConfidence()))) errors.add(error(prefix + ".confidence", "REASON_CONFIDENCE_INVALID", "reason confidence is invalid", reason.getConfidence()));
        if (reason.getRuleIds() == null || reason.getRuleIds().isEmpty()) errors.add(error(prefix + ".ruleIds", "RULE_IDS_EMPTY", "ruleIds must not be empty", null));
        if (blank(reason.getSuggestion())) errors.add(error(prefix + ".suggestion", "SUGGESTION_BLANK", "suggestion must not be blank", null));
        if (reason.getEvidence() == null || reason.getEvidence().isEmpty()) errors.add(error(prefix + ".evidence", "EVIDENCE_EMPTY", "evidence must not be empty", null));
        if (reason.getNextActions() == null || reason.getNextActions().isEmpty()) errors.add(error(prefix + ".nextActions", "NEXT_ACTIONS_EMPTY", "reason nextActions must not be empty", null));
        if (containsForbiddenClaim(reason.getDescription()) || containsForbiddenClaim(reason.getSuggestion())) {
            errors.add(error(prefix, "FORBIDDEN_CLAIM", "reason contains a forbidden write claim", reason.getCode()));
        }
        return errors;
    }

    /** 判断规则要求的证据路径是否存在于本轮成功工具输出。 */
    private boolean existsInToolFacts(List<JsonNode> outputs, String path) {
        String[] segments = path.split("\\.");
        for (JsonNode output : outputs) if (matchesPath(output, segments, 0)) return true;
        return false;
    }

    /** 递归匹配普通 JSON 路径及 items[] 数组路径。 */
    private boolean matchesPath(JsonNode node, String[] segments, int index) {
        if (node == null || index >= segments.length) return index >= segments.length;
        String segment = segments[index];
        if (segment.endsWith("[]")) {
            JsonNode array = node.get(segment.substring(0, segment.length() - 2));
            if (array == null || !array.isArray()) return false;
            for (JsonNode item : array) if (matchesPath(item, segments, index + 1)) return true;
            return false;
        }
        JsonNode child = node.get(segment);
        return child != null && matchesPath(child, segments, index + 1);
    }

    /** 将规则列表索引为规则 ID，供原因引用校验使用。 */
    private Map<String, DiagnosisRule> rulesById(RuleRegistry registry) {
        Map<String, DiagnosisRule> result = new HashMap<>();
        for (DiagnosisRule rule : registry.getRules()) if (rule != null && !blank(rule.getRuleId())) result.put(rule.getRuleId(), rule);
        return result;
    }

    /** 构造不包含业务推断的人工复核原因。 */
    private DiagnosisReasonDto fallbackReason() {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("AI_RESULT_INVALID"); reason.setTitle("AI 诊断结果不可用"); reason.setLevel("LOW"); reason.setConfidence("LOW");
        reason.setDescription("模型输出未通过规则、工具事实或安全校验。");
        reason.setSuggestion("请人工核对客户、订单、排餐记录和候选菜配置。");
        reason.setNextActions(List.of("核对客户档案", "核对订单有效性", "核对排餐记录", "核对候选菜配置"));
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("fallback", "true")));
        return reason;
    }

    /** 标记通过校验的原因仅为 AI 建议，不把建议伪装成写操作结果。 */
    private void markSuggestions(DiagnosisResponse response) {
        if (response == null || response.getReasons() == null) return;
        response.getReasons().forEach(reason -> { if (reason != null) reason.setSuggestionType("AI_SUGGESTION"); });
    }

    /** 检测回答或原因中是否出现已执行写操作的声称。 */
    private boolean containsForbiddenClaim(String value) {
        return value != null && FORBIDDEN_WRITE_CLAIMS.stream().anyMatch(value::contains);
    }

    /** 判断诊断文本是否为空。 */
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    /** 将置信度或级别归一化为大写枚举值。 */
    private String level(String value) { return value == null ? "" : value.trim().toUpperCase(); }

    /** 构造不携带原始敏感值的稳定校验错误。 */
    private DiagnosisValidationError error(String field, String code, String message, Object rawValue) {
        return new DiagnosisValidationError(field, code, message, digest(String.valueOf(rawValue)));
    }

    /** 对错误原始值计算短摘要，便于审计而不保存原文。 */
    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < Math.min(bytes.length, 6); i++) result.append(String.format("%02x", bytes[i]));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(value == null ? 0 : value.hashCode());
        }
    }
}
