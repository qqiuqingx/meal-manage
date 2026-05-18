package me.zhengjie.agent.validator;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.junit.jupiter.api.Test;

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
        response.setReasons(List.of(reason("HIGH")));

        DiagnosisResponse validated = validator.validateOrFallback(response, context(), "digest-1");

        assertEquals("最可能原因是命中客户排除日期。", validated.getSummary());
        assertFalse(validated.isFallback());
    }

    @Test
    void shouldFallbackWhenResultClaimsWriteOperation() {
        DiagnosisResultValidator validator = new DiagnosisResultValidator();
        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("已修改数据库并修复排餐。");
        response.setReasons(List.of(reason("HIGH")));

        DiagnosisResponse validated = validator.validateOrFallback(response, context(), "digest-1");

        assertTrue(validated.isFallback());
        assertEquals("AI 诊断结果不可用，建议按固定清单人工核对。", validated.getSummary());
    }

    private DiagnosisReasonDto reason(String level) {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("CUSTOMER_EXCLUDE_DATE_HIT");
        reason.setTitle("命中客户排除日期");
        reason.setLevel(level);
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("ruleId", "CUSTOMER_EXCLUDE_DATE_HIT")));
        return reason;
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
