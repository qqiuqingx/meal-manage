package me.zhengjie.agent.action;

import me.zhengjie.agent.domain.dto.DiagnosisActionDraftDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuleBasedDiagnosisActionDraftServiceTest {

    private final RuleBasedDiagnosisActionDraftService service = new RuleBasedDiagnosisActionDraftService();

    @Test
    void shouldBuildActionDraftsFromReasonCodes() {
        DiagnosisResponse response = response(
            reason("CUSTOMER_NOT_FOUND"),
            reason("ORDER_MISSING"),
            reason("CUSTOMER_EXCLUDE_DATE_HIT"),
            reason("ORDER_EXPIRED"),
            reason("ORDER_REMAINING_COUNT_NOT_ENOUGH"),
            reason("CANDIDATE_DISH_EMPTY"),
            reason("MEAL_PLAN_GENERATION_FAILED")
        );

        DiagnosisResponse applied = service.applyActionDrafts(response);

        List<String> actionCodes = applied.getActionDrafts().stream()
            .map(DiagnosisActionDraftDto::getActionCode)
            .toList();
        assertEquals(List.of(
            "CREATE_CUSTOMER_PROFILE_DRAFT",
            "CREATE_CUSTOMER_ORDER_DRAFT",
            "RESUME_CUSTOMER_DELIVERY",
            "ADJUST_ORDER_EFFECTIVE_DATE",
            "RECALCULATE_ORDER_BALANCE",
            "SUPPLEMENT_DISH_CANDIDATES",
            "REGENERATE_MEAL_PLAN"
        ), actionCodes);
        assertEquals("/api/agent/action-drafts/confirm", applied.getActionDrafts().get(0).getConfirmApi());
        assertEquals("MANUAL_CONFIRM_REQUIRED", applied.getActionDrafts().get(0).getAfterPreview().get("executeMode"));
        assertEquals("customerProfile:add", applied.getActionDrafts().get(0).getRequiredPermission());
        assertEquals("customerOrder:add", applied.getActionDrafts().get(1).getRequiredPermission());
        assertEquals("customerProfile:edit", applied.getActionDrafts().get(2).getRequiredPermission());
        assertFalse(applied.getActionDrafts().get(0).getBeforeSnapshot().isEmpty());
        DiagnosisActionDraftDto orderDraft = applied.getActionDrafts().get(3);
        assertEquals("2001", orderDraft.getTargetId());
        assertEquals("2026-05-17", orderDraft.getAfterPreview().get("newEndDate"));
        assertEquals("customerOrder:edit", orderDraft.getRequiredPermission());
        DiagnosisActionDraftDto balanceDraft = applied.getActionDrafts().get(4);
        assertEquals("2001", balanceDraft.getTargetId());
        assertEquals("2001", balanceDraft.getAfterPreview().get("orderId"));
        assertEquals("customerOrder:edit", balanceDraft.getRequiredPermission());
        assertEquals("dish:edit", applied.getActionDrafts().get(5).getRequiredPermission());
        assertEquals("mealPlan:generate", applied.getActionDrafts().get(6).getRequiredPermission());
    }

    @Test
    void shouldUseManualRecheckDraftForFallbackResult() {
        DiagnosisResponse response = response();
        response.setFallback(true);
        response.setFallbackReason("工具调用失败");

        DiagnosisResponse applied = service.applyActionDrafts(response);

        assertEquals(1, applied.getActionDrafts().size());
        assertEquals("CREATE_MANUAL_RECHECK_TASK", applied.getActionDrafts().get(0).getActionCode());
        assertEquals("LOW", applied.getActionDrafts().get(0).getRiskLevel());
        assertEquals("agentDiagnosis:confirm", applied.getActionDrafts().get(0).getRequiredPermission());
    }

    /**
     * 构造带基础上下文的诊断结果，便于校验动作草稿目标和预览字段。
     */
    private DiagnosisResponse response(DiagnosisReasonDto... reasons) {
        DiagnosisResponse response = new DiagnosisResponse();
        response.setCustomerId(1001L);
        response.setCustomerName("张三");
        response.setRecordDate("2026-05-17");
        response.setMealType("LUNCH");
        response.setReasons(List.of(reasons));
        return response;
    }

    /**
     * 构造包含证据的诊断原因，覆盖变更前快照生成逻辑。
     */
    private DiagnosisReasonDto reason(String code) {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode(code);
        reason.setTitle(code);
        reason.setRuleIds(List.of(code + "_RULE"));
        reason.setEvidence(List.of(
            new DiagnosisEvidenceDto("fieldReference", "value"),
            new DiagnosisEvidenceDto("orders.orderId", "2001"),
            new DiagnosisEvidenceDto("orders.startDate", "2026-05-01"),
            new DiagnosisEvidenceDto("orders.endDate", "2026-05-16")
        ));
        return reason;
    }
}
