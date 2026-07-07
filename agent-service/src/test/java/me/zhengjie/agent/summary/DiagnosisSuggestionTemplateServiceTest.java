package me.zhengjie.agent.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisSuggestionTemplateServiceTest {

    @Test
    void shouldLoadTemplatesAndEnsureRequiredCodesExist() {
        FileSystemDiagnosisSuggestionTemplateService service = new FileSystemDiagnosisSuggestionTemplateService(
            new org.springframework.core.io.ClassPathResource(FileSystemDiagnosisSuggestionTemplateService.DEFAULT_RESOURCE_PATH),
            new ObjectMapper()
        );

        List<DiagnosisSuggestionTemplate> templates = service.listTemplates();

        assertTrue(templates.stream().anyMatch(template -> "CUSTOMER_NOT_FOUND".equals(template.getCode())));
        assertTrue(templates.stream().anyMatch(template -> "ORDER_MISSING".equals(template.getCode())));
        assertTrue(templates.stream().anyMatch(template -> "ORDER_NOT_EFFECTIVE".equals(template.getCode())));
        assertTrue(templates.stream().anyMatch(template -> "ORDER_REMAINING_COUNT_EMPTY".equals(template.getCode())));
        assertTrue(templates.stream().anyMatch(template -> "SCHEDULE_MODE_NOT_MATCH".equals(template.getCode())));
        assertTrue(templates.stream().anyMatch(template -> "CUSTOMER_EXCLUDE_DATE_HIT".equals(template.getCode())));
        assertTrue(templates.stream().anyMatch(template -> "CANDIDATE_DISH_EMPTY".equals(template.getCode())));
        assertTrue(templates.stream().anyMatch(template -> "DISH_FILTERED_EMPTY".equals(template.getCode())));
        assertTrue(templates.stream().anyMatch(template -> "MEAL_PLAN_GENERATED_FAILED".equals(template.getCode())));
        assertTrue(templates.stream().anyMatch(template -> "AI_RESULT_INVALID".equals(template.getCode())));
    }

    @Test
    void shouldRejectDuplicateTemplateCode() {
        FileSystemDiagnosisSuggestionTemplateService service = new FileSystemDiagnosisSuggestionTemplateService(new ByteArrayResource("""
            templates:
              - code: CUSTOMER_NOT_FOUND
                title: one
                defaultSuggestion: a
                nextActions: [x]
                customerVisible: false
                requiresManualConfirm: true
              - code: CUSTOMER_NOT_FOUND
                title: two
                defaultSuggestion: b
                nextActions: [y]
                customerVisible: false
                requiresManualConfirm: true
            """.getBytes()), new ObjectMapper());

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::listTemplates);

        assertTrue(ex.getMessage().contains("duplicate"));
    }

    @Test
    void shouldApplyTemplateSuggestionAndNextActions() {
        FileSystemDiagnosisSuggestionTemplateService service = new FileSystemDiagnosisSuggestionTemplateService(
            new org.springframework.core.io.ClassPathResource(FileSystemDiagnosisSuggestionTemplateService.DEFAULT_RESOURCE_PATH),
            new ObjectMapper()
        );
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("CUSTOMER_EXCLUDE_DATE_HIT");
        reason.setTitle("命中客户停送日期");
        reason.setLevel("HIGH");
        reason.setConfidence("HIGH");
        reason.setRuleIds(List.of("CUSTOMER_EXCLUDE_DATE_HIT"));
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("excludeDate", "2026-05-22")));
        DiagnosisResponse response = new DiagnosisResponse();
        response.setSummary("命中停送日期");
        response.setConfidence("HIGH");
        response.setReasons(List.of(reason));

        DiagnosisResponse applied = service.applyTemplates(response);

        assertEquals("请先核对客户停送登记，如需恢复配送，按客户管理流程调整后重新生成排餐。", applied.getReasons().get(0).getSuggestion());
        assertEquals(List.of("核对客户档案停送配置", "确认是否需要恢复配送"), applied.getReasons().get(0).getNextActions());
        assertEquals(List.of("核对客户档案停送配置", "确认是否需要恢复配送"), applied.getNextActions());
    }
}
