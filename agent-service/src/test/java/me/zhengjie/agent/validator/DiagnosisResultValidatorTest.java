package me.zhengjie.agent.validator;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.rule.FileSystemRuleRegistryLoader;
import me.zhengjie.agent.rule.RuleRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisResultValidatorTest {

    @Test
    void shouldAcceptValidStructuredResult() {
        DiagnosisResultValidator validator = new DiagnosisResultValidator();
        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("最可能原因是命中客户排除日期。");
        response.setConfidence("HIGH");
        response.setNextActions(List.of("核对客户档案停送配置"));
        response.setReasons(List.of(reason("HIGH")));

        DiagnosisResponse validated = validator.validateOrFallback(response, context(), registry());

        assertEquals("最可能原因是命中客户排除日期。", validated.getSummary());
        assertFalse(validated.isFallback());
    }

    @Test
    void shouldFallbackWhenResultClaimsWriteOperation() {
        DiagnosisResultValidator validator = new DiagnosisResultValidator();
        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("已修改数据库并修复排餐。");
        response.setConfidence("HIGH");
        response.setNextActions(List.of("核对客户档案停送配置"));
        response.setReasons(List.of(reason("HIGH")));

        DiagnosisResponse validated = validator.validateOrFallback(response, context(), registry());

        assertTrue(validated.isFallback());
        assertEquals("AI 诊断结果不可用，建议按固定清单人工核对。", validated.getSummary());
        assertEquals("AI 诊断结果校验失败，需人工核对。", validated.getFallbackReason());
    }

    @Test
    void shouldReportValidationErrorsForMissingRuleIdsEvidenceAndNextActions() {
        DiagnosisResultValidator validator = new DiagnosisResultValidator();
        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("命中客户排除日期。");
        response.setConfidence("HIGH");
        response.setNextActions(List.of("核对客户档案"));
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("CUSTOMER_EXCLUDE_DATE_HIT");
        reason.setTitle("命中客户排除日期");
        reason.setLevel("HIGH");
        reason.setConfidence("HIGH");
        response.setReasons(List.of(reason));

        List<DiagnosisValidationError> errors = validator.validate(response);

        assertTrue(errors.stream().anyMatch(error -> "RULE_IDS_EMPTY".equals(error.getCode())));
        assertTrue(errors.stream().anyMatch(error -> "EVIDENCE_EMPTY".equals(error.getCode())));
        assertTrue(errors.stream().anyMatch(error -> "NEXT_ACTIONS_EMPTY".equals(error.getCode())));
    }

    @Test
    void shouldRejectFallbackWithoutFallbackReason() {
        DiagnosisResultValidator validator = new DiagnosisResultValidator();
        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("诊断数据不完整，需人工核对。");
        response.setConfidence("LOW");
        response.setFallback(true);
        response.setNextActions(List.of("核对客户档案"));
        response.setReasons(List.of(reason("LOW")));

        List<DiagnosisValidationError> errors = validator.validate(response);

        assertTrue(errors.stream().anyMatch(error -> "FALLBACK_REASON_BLANK".equals(error.getCode())));
    }

    @Test
    void shouldRejectUnknownRuleIdAndEvidenceOutsideRuleFields() {
        DiagnosisResultValidator validator = new DiagnosisResultValidator();
        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("最可能原因是命中客户排除日期。");
        response.setConfidence("HIGH");
        response.setNextActions(List.of("核对客户档案停送配置"));
        DiagnosisReasonDto reason = reason("HIGH");
        reason.setRuleIds(List.of("UNKNOWN_RULE"));
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("ruleId", "UNKNOWN_RULE")));
        response.setReasons(List.of(reason));

        List<DiagnosisValidationError> errors = validator.validate(response, registry());

        assertTrue(errors.stream().anyMatch(error -> "RULE_ID_UNKNOWN".equals(error.getCode())));
        assertTrue(errors.stream().anyMatch(error -> "EVIDENCE_LABEL_NOT_ALLOWED".equals(error.getCode())));
        assertTrue(errors.stream().anyMatch(error -> "BUSINESS_EVIDENCE_EMPTY".equals(error.getCode())));
    }

    private DiagnosisReasonDto reason(String level) {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("CUSTOMER_EXCLUDE_DATE_HIT");
        reason.setTitle("命中客户排除日期");
        reason.setLevel(level);
        reason.setConfidence(level);
        reason.setRuleIds(List.of("CUSTOMER_EXCLUDE_DATE_HIT"));
        reason.setSuggestion("请先核对客户停送登记。");
        reason.setNextActions(List.of("核对客户档案停送配置"));
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("customerProfile.excludeDates", "2026-05-17:LUNCH")));
        return reason;
    }

    private RuleRegistry registry() {
        return new FileSystemRuleRegistryLoader(Path.of("rules")).load("MEAL_PLAN_NOT_GENERATED");
    }

    private DiagnosisContextDto context() {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setCustomerId(1001L);
        context.setCustomerName("张三");
        context.setRecordDate("2026-05-17");
        context.setMealType("LUNCH");
        return context;
    }
}
