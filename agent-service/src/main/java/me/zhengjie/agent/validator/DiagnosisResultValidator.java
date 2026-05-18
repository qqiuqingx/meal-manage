package me.zhengjie.agent.validator;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;

import java.util.List;
import java.util.Set;

/**
 * 校验 AI 输出是否适合直接展示给客服。
 */
public class DiagnosisResultValidator {

    private static final Set<String> ALLOWED_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");
    private static final List<String> FORBIDDEN_WRITE_CLAIMS = List.of("已修复", "已修改数据库", "已创建客户");

    public DiagnosisResponse validateOrFallback(DiagnosisResponse response,
                                                DiagnosisContextDto context,
                                                String ruleVersionDigest) {
        if (isValid(response)) {
            fillContext(response, context, ruleVersionDigest, false);
            return response;
        }

        DiagnosisResponse fallback = new DiagnosisResponse();
        fallback.setSummary("AI 诊断结果不可用，建议按固定清单人工核对。");
        fallback.setReasons(List.of(fallbackReason()));
        fillContext(fallback, context, ruleVersionDigest, true);
        return fallback;
    }

    private boolean isValid(DiagnosisResponse response) {
        if (response == null || isBlank(response.getSummary()) || containsForbiddenClaim(response.getSummary())) {
            return false;
        }
        if (response.getReasons() == null) {
            return false;
        }
        return response.getReasons().stream().allMatch(this::isValidReason);
    }

    private boolean isValidReason(DiagnosisReasonDto reason) {
        if (reason == null || isBlank(reason.getCode()) || !ALLOWED_LEVELS.contains(reason.getLevel())) {
            return false;
        }
        if (containsForbiddenClaim(reason.getDescription()) || containsForbiddenClaim(reason.getSuggestion())) {
            return false;
        }
        return reason.getEvidence() != null && !reason.getEvidence().isEmpty();
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
        reason.setDescription("模型输出为空、格式不合法或包含不允许的写操作表述。");
        reason.setSuggestion("请人工核对客户、订单、排餐记录和候选菜配置。");
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
}
