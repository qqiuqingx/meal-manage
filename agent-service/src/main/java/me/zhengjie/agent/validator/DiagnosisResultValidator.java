package me.zhengjie.agent.validator;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.rule.DiagnosisRule;
import me.zhengjie.agent.rule.RuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 校验 AI 输出是否适合直接展示给客服。
 */
public class DiagnosisResultValidator {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisResultValidator.class);
    private static final Set<String> ALLOWED_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");
    private static final List<String> FORBIDDEN_WRITE_CLAIMS = List.of("已修复", "已修改数据库", "已创建客户");

    public DiagnosisResponse validateOrFallback(DiagnosisResponse response,
                                                DiagnosisContextDto context,
                                                String ruleVersionDigest) {
        List<DiagnosisValidationError> errors = validate(response);
        return validateOrFallback(response, context, ruleVersionDigest, errors);
    }

    /**
     * 按当前规则注册表校验诊断结果，防止模型引用不存在的规则或伪造证据字段。
     */
    public DiagnosisResponse validateOrFallback(DiagnosisResponse response,
                                                DiagnosisContextDto context,
                                                RuleRegistry ruleRegistry) {
        String ruleVersionDigest = ruleRegistry == null ? null : ruleRegistry.getVersionDigest();
        List<DiagnosisValidationError> errors = validate(response, ruleRegistry);
        return validateOrFallback(response, context, ruleVersionDigest, errors);
    }

    private DiagnosisResponse validateOrFallback(DiagnosisResponse response,
                                                DiagnosisContextDto context,
                                                String ruleVersionDigest,
        List<DiagnosisValidationError> errors) {
        if (errors.isEmpty()) {
            fillContext(response, context, ruleVersionDigest, response.isFallback());
            return response;
        }
        log.warn("诊断阶段 stage=结果校验失败 errors={}", summarizeErrors(errors));

        DiagnosisResponse fallback = new DiagnosisResponse();
        fallback.setSummary("AI 诊断结果不可用，建议按固定清单人工核对。");
        fallback.setReasons(List.of(fallbackReason()));
        fallback.setConfidence("LOW");
        fallback.setNextActions(List.of("核对客户档案", "核对订单有效性", "核对排餐记录", "核对候选菜配置"));
        fillContext(fallback, context, ruleVersionDigest, true);
        fallback.setFallbackReason("AI 诊断结果校验失败，需人工核对。");
        return fallback;
    }

    /**
     * 校验诊断结果结构，返回错误列表而不是布尔值，便于观测具体失败原因。
     *
     * @param response 模型返回的诊断结果
     * @return 校验错误列表；为空表示通过
     */
    public List<DiagnosisValidationError> validate(DiagnosisResponse response) {
        return validate(response, null);
    }

    /**
     * 校验诊断结果结构和规则一致性。
     *
     * @param response 模型返回的诊断结果
     * @param ruleRegistry 当前诊断场景生效的规则注册表；为空时只做基础结构校验
     * @return 校验错误列表；为空表示通过
     */
    public List<DiagnosisValidationError> validate(DiagnosisResponse response, RuleRegistry ruleRegistry) {
        List<DiagnosisValidationError> errors = new ArrayList<>();
        if (response == null) {
            errors.add(error("response", "RESPONSE_NULL", "response must not be null", null));
            return errors;
        }
        if (isBlank(response.getSummary())) {
            errors.add(error("summary", "SUMMARY_BLANK", "summary must not be blank", response.getSummary()));
        } else if (containsForbiddenClaim(response.getSummary())) {
            errors.add(error("summary", "SUMMARY_FORBIDDEN_CLAIM", "summary contains forbidden write claim", response.getSummary()));
        }
        if (!ALLOWED_LEVELS.contains(normalizeLevel(response.getConfidence()))) {
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
        if (!response.isFallback() && response.getReasons() != null && response.getReasons().size() == 1
            && response.getReasons().stream().allMatch(reason -> reason != null && "AI_RESULT_INVALID".equals(reason.getCode()))) {
            errors.add(error("fallback", "FALLBACK_REASON_ONLY", "non-fallback response must not contain only fallback reason", response.getReasons()));
        }
        if (response.isFallback() && isBlank(response.getFallbackReason())) {
            errors.add(error("fallbackReason", "FALLBACK_REASON_BLANK", "fallbackReason must not be blank when fallback=true", response.getFallbackReason()));
        }
        if (!response.isFallback()) {
            errors.addAll(validateRulesAndEvidence(response, ruleRegistry));
        }
        return errors;
    }

    private List<DiagnosisValidationError> validateRulesAndEvidence(DiagnosisResponse response, RuleRegistry ruleRegistry) {
        List<DiagnosisValidationError> errors = new ArrayList<>();
        if (ruleRegistry == null || ruleRegistry.getRules() == null || ruleRegistry.getRules().isEmpty()
            || response.getReasons() == null || response.getReasons().isEmpty()) {
            return errors;
        }
        Map<String, DiagnosisRule> rulesById = rulesById(ruleRegistry);
        boolean hasBusinessEvidence = false;
        for (int i = 0; i < response.getReasons().size(); i++) {
            DiagnosisReasonDto reason = response.getReasons().get(i);
            if (reason == null || reason.getRuleIds() == null || reason.getRuleIds().isEmpty()) {
                continue;
            }
            Set<String> allowedEvidenceFields = new HashSet<>();
            for (String ruleId : reason.getRuleIds()) {
                DiagnosisRule rule = rulesById.get(ruleId);
                if (rule == null) {
                    errors.add(error("reasons[" + i + "].ruleIds", "RULE_ID_UNKNOWN", "ruleId must exist in registry", ruleId));
                    continue;
                }
                if (!isBlank(rule.getReasonCode()) && !rule.getReasonCode().equals(reason.getCode())) {
                    errors.add(error("reasons[" + i + "].code", "REASON_CODE_RULE_MISMATCH",
                        "reason code must match rule reasonCode", reason.getCode() + "|" + rule.getReasonCode()));
                }
                if (rule.getEvidenceFields() != null) {
                    allowedEvidenceFields.addAll(rule.getEvidenceFields());
                }
            }
            if (reason.getEvidence() == null || reason.getEvidence().isEmpty()) {
                continue;
            }
            for (int evidenceIndex = 0; evidenceIndex < reason.getEvidence().size(); evidenceIndex++) {
                DiagnosisEvidenceDto evidence = reason.getEvidence().get(evidenceIndex);
                String label = evidence == null ? null : evidence.getLabel();
                if (isBlank(label)) {
                    errors.add(error("reasons[" + i + "].evidence[" + evidenceIndex + "].label",
                        "EVIDENCE_LABEL_BLANK", "evidence label must not be blank", label));
                } else if (!allowedEvidenceFields.contains(label)) {
                    errors.add(error("reasons[" + i + "].evidence[" + evidenceIndex + "].label",
                        "EVIDENCE_LABEL_NOT_ALLOWED", "evidence label must be defined by referenced rules", label));
                } else {
                    hasBusinessEvidence = true;
                }
            }
        }
        if (!hasBusinessEvidence) {
            errors.add(error("evidence", "BUSINESS_EVIDENCE_EMPTY",
                "non-fallback response must contain at least one rule-defined business evidence field", response.getReasons()));
        }
        return errors;
    }

    private Map<String, DiagnosisRule> rulesById(RuleRegistry ruleRegistry) {
        Map<String, DiagnosisRule> rulesById = new HashMap<>();
        for (DiagnosisRule rule : ruleRegistry.getRules()) {
            if (rule != null && !isBlank(rule.getRuleId())) {
                rulesById.put(rule.getRuleId(), rule);
            }
        }
        return rulesById;
    }

    private List<DiagnosisValidationError> validateReason(DiagnosisReasonDto reason, int index) {
        List<DiagnosisValidationError> errors = new ArrayList<>();
        if (reason == null) {
            errors.add(error("reasons[" + index + "]", "REASON_NULL", "reason must not be null", null));
            return errors;
        }
        String fieldPrefix = "reasons[" + index + "]";
        if (isBlank(reason.getCode())) {
            errors.add(error(fieldPrefix + ".code", "REASON_CODE_BLANK", "reason code must not be blank", reason.getCode()));
        }
        if (!ALLOWED_LEVELS.contains(normalizeLevel(reason.getLevel()))) {
            errors.add(error(fieldPrefix + ".level", "REASON_LEVEL_INVALID", "reason level must be HIGH, MEDIUM or LOW", reason.getLevel()));
        }
        if (!ALLOWED_LEVELS.contains(normalizeLevel(reason.getConfidence()))) {
            errors.add(error(fieldPrefix + ".confidence", "REASON_CONFIDENCE_INVALID", "reason confidence must be HIGH, MEDIUM or LOW", reason.getConfidence()));
        }
        if (reason.getRuleIds() == null || reason.getRuleIds().isEmpty()) {
            errors.add(error(fieldPrefix + ".ruleIds", "RULE_IDS_EMPTY", "ruleIds must not be empty", reason.getRuleIds()));
        }
        if (isBlank(reason.getSuggestion())) {
            errors.add(error(fieldPrefix + ".suggestion", "SUGGESTION_BLANK", "reason suggestion must not be blank", reason.getSuggestion()));
        }
        if (reason.getEvidence() == null || reason.getEvidence().isEmpty()) {
            errors.add(error(fieldPrefix + ".evidence", "EVIDENCE_EMPTY", "evidence must not be empty", reason.getEvidence()));
        }
        if (reason.getNextActions() == null || reason.getNextActions().isEmpty()) {
            errors.add(error(fieldPrefix + ".nextActions", "NEXT_ACTIONS_EMPTY", "reason nextActions must not be empty", reason.getNextActions()));
        }
        if (containsForbiddenClaim(reason.getDescription()) || containsForbiddenClaim(reason.getSuggestion())) {
            errors.add(error(fieldPrefix, "FORBIDDEN_CLAIM", "reason contains forbidden write claim", reason.getDescription() + "|" + reason.getSuggestion()));
        }
        return errors;
    }

    private boolean containsForbiddenClaim(String value) {
        if (value == null) {
            return false;
        }
        return FORBIDDEN_WRITE_CLAIMS.stream().anyMatch(value::contains);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private DiagnosisReasonDto fallbackReason() {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("AI_RESULT_INVALID");
        reason.setTitle("AI 诊断结果不可用");
        reason.setLevel("LOW");
        reason.setConfidence("LOW");
        reason.setRuleIds(List.of("AI_RESULT_INVALID"));
        reason.setDescription("模型输出为空、格式不合法或包含不允许的写操作表述。");
        reason.setSuggestion("请人工核对客户、订单、排餐记录和候选菜配置。");
        reason.setNextActions(List.of("核对客户档案", "核对订单有效性", "核对排餐记录", "核对候选菜配置"));
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("fallback", "true")));
        return reason;
    }

    private void fillContext(DiagnosisResponse response,
                             DiagnosisContextDto context,
                             String ruleVersionDigest,
                             boolean fallback) {
        response.setCustomerId(context.getCustomerId());
        response.setCustomerName(context.getCustomerName());
        response.setRecordDate(context.getRecordDate());
        response.setMealType(context.getMealType());
        response.setRuleVersionDigest(ruleVersionDigest);
        response.setFallback(fallback);
    }

    private DiagnosisValidationError error(String field, String code, String message, Object rawValue) {
        return new DiagnosisValidationError(field, code, message, digest(String.valueOf(rawValue)));
    }

    private String summarizeErrors(List<DiagnosisValidationError> errors) {
        return errors.stream()
            .map(error -> error.getField() + ":" + error.getCode() + ":" + error.getRawValueDigest())
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    }

    private String normalizeLevel(String level) {
        return level == null ? "" : level.trim().toUpperCase();
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(6, bytes.length); i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value == null ? 0 : value.hashCode());
        }
    }
}
