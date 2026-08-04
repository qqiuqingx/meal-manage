package me.zhengjie.agent.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.rule.DiagnosisRule;
import me.zhengjie.agent.rule.RuleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 结构化诊断结果必须引用当前规则和本轮成功工具事实。 */
class DiagnosisResultValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 规则版本、必需工具和证据路径均满足时允许展示。 */
    @Test
    void shouldAcceptRuleBackedDiagnosis() {
        RuleRegistry registry = registry("digest-1", "getServiceCustomerDetail", "data.profile");
        DiagnosisResultValidator validator = new DiagnosisResultValidator(objectMapper);

        DiagnosisResponse response = response("digest-1", "CUSTOMER_NOT_FOUND", "CUSTOMER_NOT_FOUND", "data.profile");
        ToolExecutionContext.ToolFact fact = fact("getServiceCustomerDetail", "{\"data\":{\"profile\":null}}");

        assertTrue(validator.validate(response, registry, List.of(fact)).isEmpty());
        assertEquals("AI_SUGGESTION", response.getReasons().get(0).getSuggestionType());
    }

    /** 未成功调用规则要求的工具时必须拒绝诊断结果。 */
    @Test
    void shouldRejectDiagnosisWithoutRequiredTool() {
        RuleRegistry registry = registry("digest-1", "getServiceCustomerDetail", "data.profile");
        DiagnosisResultValidator validator = new DiagnosisResultValidator(objectMapper);

        List<DiagnosisValidationError> errors = validator.validate(
            response("digest-1", "CUSTOMER_NOT_FOUND", "CUSTOMER_NOT_FOUND", "data.profile"), registry, List.of());

        assertTrue(errors.stream().anyMatch(error -> "REQUIRED_TOOL_NOT_CALLED".equals(error.getCode())));
        assertTrue(errors.stream().anyMatch(error -> "BUSINESS_EVIDENCE_EMPTY".equals(error.getCode())));
    }

    private RuleRegistry registry(String digest, String requiredTool, String evidenceField) {
        DiagnosisRule rule = new DiagnosisRule();
        rule.setRuleId("CUSTOMER_NOT_FOUND"); rule.setReasonCode("CUSTOMER_NOT_FOUND"); rule.setVersion(1);
        rule.setRequiredTools(List.of(requiredTool)); rule.setEvidenceFields(List.of(evidenceField));
        rule.setNextActions(List.of("核对客户档案")); rule.setOwner("test");
        RuleRegistry registry = new RuleRegistry(); registry.setVersionDigest(digest); registry.setRules(List.of(rule));
        return registry;
    }

    private DiagnosisResponse response(String digest, String code, String ruleId, String evidenceField) {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode(code); reason.setTitle("客户不存在"); reason.setLevel("HIGH"); reason.setConfidence("HIGH");
        reason.setRuleIds(List.of(ruleId)); reason.setSuggestion("核对客户档案"); reason.setNextActions(List.of("核对客户档案"));
        reason.setEvidence(List.of(new DiagnosisEvidenceDto(evidenceField, "未命中")));
        DiagnosisResponse response = new DiagnosisResponse(); response.setSummary("客户档案未命中"); response.setConfidence("HIGH");
        response.setRuleVersionDigest(digest); response.setReasons(List.of(reason)); response.setNextActions(List.of("核对客户档案"));
        return response;
    }

    private ToolExecutionContext.ToolFact fact(String name, String output) {
        return new ToolExecutionContext.ToolFact("call-1", name, "SERVICE_CUSTOMER_DETAIL", output, true, 1);
    }
}
