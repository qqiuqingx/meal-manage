package me.zhengjie.modules.agent.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.agent.domain.AgentActionAudit;
import me.zhengjie.modules.agent.domain.dto.AgentActionAuditQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmRequest;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisActionDraftDto;
import me.zhengjie.modules.agent.mapper.AgentActionAuditMapper;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderBalanceRecalculateResult;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderEffectiveDateAdjustResult;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentResult;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentActionConfirmServiceImplTest {

    @Mock
    private AgentActionAuditMapper actionAuditMapper;

    @Mock
    private MealPlanService mealPlanService;

    @Mock
    private CustomerProfileService customerProfileService;

    @Mock
    private CustomerOrderService customerOrderService;

    @InjectMocks
    private AgentActionConfirmServiceImpl service;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "admin",
            null,
            Collections.singletonList(new SimpleGrantedAuthority("admin"))
        ));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExecuteRegenerateMealPlanAfterManualConfirmation() {
        stubInsertId();
        MealPlanGenerateResult generateResult = new MealPlanGenerateResult();
        generateResult.setMealPlanId(99L);
        generateResult.setRecordDate("2026-07-08");
        generateResult.setMealType("LUNCH");
        generateResult.setTotalCount(3);
        generateResult.setSuccessCount(3);
        generateResult.setFailCount(0);
        when(mealPlanService.generateMealPlan("2026-07-08", "LUNCH", 1001L)).thenReturn(generateResult);

        AgentActionConfirmResponse response = service.confirm(request(regenerateDraft("2026-07-08", "LUNCH", 1001L)));

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        AgentActionAudit audit = captor.getValue();
        assertEquals("EXECUTED", audit.getStatus());
        assertTrue(audit.getSuccess());
        assertNotNull(audit.getExecutionResult());
        assertEquals("EXECUTED", response.getStatus());
        assertTrue(response.getSuccess());
        assertNotNull(response.getExecutionResult());
        verify(mealPlanService).generateMealPlan("2026-07-08", "LUNCH", 1001L);
    }

    @Test
    void shouldExecuteResumeCustomerDeliveryAfterManualConfirmation() {
        stubInsertId();
        CustomerMealScheduleAdjustmentResult adjustmentResult = new CustomerMealScheduleAdjustmentResult();
        adjustmentResult.setCustomerId(1001L);
        adjustmentResult.setExcludedMealCount(0);
        adjustmentResult.setAdditionMealCount(0);
        adjustmentResult.setDeletedUnverifiedPlanCount(0);
        when(customerProfileService.resumeCustomerDelivery(1001L, "2026-07-08", "LUNCH")).thenReturn(adjustmentResult);

        AgentActionConfirmResponse response = service.confirm(request(resumeDeliveryDraft("2026-07-08", "LUNCH", 1001L)));

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("EXECUTED", captor.getValue().getStatus());
        assertTrue(captor.getValue().getSuccess());
        assertEquals("EXECUTED", response.getStatus());
        assertTrue(response.getSuccess());
        verify(customerProfileService).resumeCustomerDelivery(1001L, "2026-07-08", "LUNCH");
        verify(mealPlanService, never()).generateMealPlan(any(), any(), any());
    }

    @Test
    void shouldRejectResumeCustomerDeliveryWhenCustomerIdMissing() {
        stubInsertId();

        AgentActionConfirmResponse response = service.confirm(request(resumeDeliveryDraft("2026-07-08", "LUNCH", null)));

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("VALIDATION_FAILED", captor.getValue().getStatus());
        assertEquals("VALIDATION_FAILED", response.getStatus());
        verify(customerProfileService, never()).resumeCustomerDelivery(any(), any(), any());
    }

    @Test
    void shouldExecuteAdjustOrderEffectiveDateAfterSecondConfirmation() {
        stubInsertId();
        CustomerOrderEffectiveDateAdjustResult adjustResult = new CustomerOrderEffectiveDateAdjustResult();
        adjustResult.setOrderId(2001L);
        adjustResult.setOldEndDate("2026-07-07");
        adjustResult.setNewEndDate("2026-07-08");
        when(customerOrderService.adjustEffectiveDate(2001L, null, "2026-07-08")).thenReturn(adjustResult);

        AgentActionConfirmRequest request = request(orderEffectiveDateDraft(2001L, null, "2026-07-08"));
        request.setSecondConfirmed(true);
        AgentActionConfirmResponse response = service.confirm(request);

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("EXECUTED", captor.getValue().getStatus());
        assertTrue(captor.getValue().getSuccess());
        assertEquals("EXECUTED", response.getStatus());
        assertTrue(response.getSuccess());
        verify(customerOrderService).adjustEffectiveDate(2001L, null, "2026-07-08");
    }

    @Test
    void shouldRejectAdjustOrderEffectiveDateWhenOrderIdMissing() {
        stubInsertId();

        AgentActionConfirmRequest request = request(orderEffectiveDateDraft(null, "2026-07-08", null));
        request.setSecondConfirmed(true);
        AgentActionConfirmResponse response = service.confirm(request);

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("VALIDATION_FAILED", captor.getValue().getStatus());
        assertEquals("VALIDATION_FAILED", response.getStatus());
        verify(customerOrderService, never()).adjustEffectiveDate(any(), any(), any());
    }

    @Test
    void shouldExecuteRecalculateOrderBalanceAfterSecondConfirmation() {
        stubInsertId();
        CustomerOrderBalanceRecalculateResult recalculateResult = new CustomerOrderBalanceRecalculateResult();
        recalculateResult.setOrderId(2001L);
        recalculateResult.setNewVerifiedCount(3);
        recalculateResult.setNewRemainingCount(17);
        when(customerOrderService.recalculateBalance(2001L)).thenReturn(recalculateResult);

        AgentActionConfirmRequest request = request(orderBalanceDraft(2001L));
        request.setSecondConfirmed(true);
        AgentActionConfirmResponse response = service.confirm(request);

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("EXECUTED", captor.getValue().getStatus());
        assertTrue(captor.getValue().getSuccess());
        assertEquals("EXECUTED", response.getStatus());
        assertTrue(response.getSuccess());
        verify(customerOrderService).recalculateBalance(2001L);
    }

    @Test
    void shouldRejectRecalculateOrderBalanceWhenOrderIdMissing() {
        stubInsertId();

        AgentActionConfirmRequest request = request(orderBalanceDraft(null));
        request.setSecondConfirmed(true);
        AgentActionConfirmResponse response = service.confirm(request);

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("VALIDATION_FAILED", captor.getValue().getStatus());
        assertEquals("VALIDATION_FAILED", response.getStatus());
        verify(customerOrderService, never()).recalculateBalance(any());
    }

    @Test
    void shouldRejectRegenerateMealPlanWhenDateAndMealTypeMissing() {
        stubInsertId();
        AgentDiagnosisActionDraftDto draft = regenerateDraft(null, null, 1001L);
        draft.setTargetId("|");

        AgentActionConfirmResponse response = service.confirm(request(draft));

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("VALIDATION_FAILED", captor.getValue().getStatus());
        assertEquals("VALIDATION_FAILED", response.getStatus());
        verify(mealPlanService, never()).generateMealPlan(any(), any(), any());
    }

    @Test
    void shouldConfirmManualRecheckAsAuditOnlyAction() {
        stubInsertId();

        AgentActionConfirmResponse response = service.confirm(request(manualRecheckDraft()));

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("CONFIRMED", captor.getValue().getStatus());
        assertEquals("CONFIRMED", response.getStatus());
        assertTrue(response.getSuccess());
        verify(mealPlanService, never()).generateMealPlan(any(), any(), any());
    }

    @Test
    void shouldConfirmSupplementDishCandidatesAsConfirmOnlyAction() {
        stubInsertId();

        AgentActionConfirmResponse response = service.confirm(request(confirmOnlyDraft(
            "SUPPLEMENT_DISH_CANDIDATES", "补充候选菜配置", "MEDIUM", "DISH_CONFIG", "2026-07-08|LUNCH")));

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("CONFIRMED", captor.getValue().getStatus());
        assertEquals("CONFIRMED", response.getStatus());
        assertTrue(response.getSuccess());
        verify(customerOrderService, never()).adjustEffectiveDate(any(), any(), any());
        verify(customerProfileService, never()).resumeCustomerDelivery(any(), any(), any());
        verify(mealPlanService, never()).generateMealPlan(any(), any(), any());
    }

    @Test
    void shouldRequireSecondConfirmationForCreateProfileDraft() {
        stubInsertId();

        AgentActionConfirmResponse response = service.confirm(request(confirmOnlyDraft(
            "CREATE_CUSTOMER_PROFILE_DRAFT", "创建客户档案草稿", "HIGH", "CUSTOMER", "1001")));

        assertEquals("NEED_SECOND_CONFIRM", response.getStatus());
        verify(actionAuditMapper, never()).updateById(any());
    }

    @Test
    void shouldRejectActionWhenRequiredPermissionMissing() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "operator",
            null,
            Collections.singletonList(new SimpleGrantedAuthority("agentDiagnosis:confirm"))
        ));
        stubInsertId();

        AgentActionConfirmRequest request = request(orderBalanceDraft(2001L));
        request.setSecondConfirmed(true);
        AgentActionConfirmResponse response = service.confirm(request);

        assertEquals("PERMISSION_DENIED", response.getStatus());
        verify(actionAuditMapper, never()).updateById(any());
        verify(customerOrderService, never()).recalculateBalance(any());
    }

    @Test
    void shouldRejectActionWhenRequiredPermissionBlank() {
        stubInsertId();
        AgentDiagnosisActionDraftDto draft = manualRecheckDraft();
        draft.setRequiredPermission(null);

        AgentActionConfirmResponse response = service.confirm(request(draft));

        assertEquals("VALIDATION_FAILED", response.getStatus());
        verify(actionAuditMapper, never()).updateById(any());
    }

    @Test
    void shouldConfirmCreateOrderDraftAfterSecondConfirmation() {
        stubInsertId();
        AgentActionConfirmRequest request = request(confirmOnlyDraft(
            "CREATE_CUSTOMER_ORDER_DRAFT", "创建客户订单草稿", "HIGH", "ORDER", "1001"));
        request.setSecondConfirmed(true);

        AgentActionConfirmResponse response = service.confirm(request);

        ArgumentCaptor<AgentActionAudit> captor = ArgumentCaptor.forClass(AgentActionAudit.class);
        verify(actionAuditMapper).updateById(captor.capture());
        assertEquals("CONFIRMED", captor.getValue().getStatus());
        assertEquals("CONFIRMED", response.getStatus());
        assertTrue(response.getSuccess());
        verify(customerOrderService, never()).recalculateBalance(any());
    }

    @Test
    void shouldQueryActionAuditsBySessionAndRequest() {
        AgentActionAudit audit = new AgentActionAudit();
        audit.setId(1L);
        audit.setRequestId("req-1");
        audit.setSessionId("session-1");
        audit.setActionCode("CREATE_MANUAL_RECHECK_TASK");
        audit.setStatus("CONFIRMED");
        Page<AgentActionAudit> page = new Page<>(1, 5);
        page.setRecords(Collections.singletonList(audit));
        page.setTotal(1);
        when(actionAuditMapper.selectPage(any(), any())).thenReturn(page);

        AgentActionAuditQueryCriteria criteria = new AgentActionAuditQueryCriteria();
        criteria.setSessionId("session-1");
        criteria.setRequestId("req-1");
        criteria.setPage(0);
        criteria.setSize(5);
        PageResult<AgentActionAudit> result = service.queryAudits(criteria);

        assertEquals(1, result.getTotalElements());
        assertEquals("session-1", result.getContent().get(0).getSessionId());
        assertEquals("CONFIRMED", result.getContent().get(0).getStatus());
        verify(actionAuditMapper).selectPage(any(), any());
    }

    private void stubInsertId() {
        when(actionAuditMapper.insert(any(AgentActionAudit.class))).thenAnswer(invocation -> {
            AgentActionAudit audit = invocation.getArgument(0);
            audit.setId(1L);
            return 1;
        });
    }

    private AgentActionConfirmRequest request(AgentDiagnosisActionDraftDto draft) {
        AgentActionConfirmRequest request = new AgentActionConfirmRequest();
        request.setRequestId("diag-001");
        request.setSessionId("session-001");
        request.setIdempotencyKey("diag-001:" + draft.getActionCode());
        request.setActionDraft(draft);
        request.setSecondConfirmed(false);
        request.setComment("人工确认");
        return request;
    }

    private AgentDiagnosisActionDraftDto regenerateDraft(String recordDate, String mealType, Long customerId) {
        AgentDiagnosisActionDraftDto draft = new AgentDiagnosisActionDraftDto();
        draft.setActionCode("REGENERATE_MEAL_PLAN");
        draft.setTitle("重新生成排餐");
        draft.setRiskLevel("MEDIUM");
        draft.setTargetType("MEAL_PLAN");
        draft.setTargetId((recordDate == null ? "" : recordDate) + "|" + (mealType == null ? "" : mealType));
        draft.setRequiredPermission("mealPlan:generate");
        Map<String, Object> afterPreview = new LinkedHashMap<>();
        afterPreview.put("recordDate", recordDate);
        afterPreview.put("mealType", mealType);
        afterPreview.put("customerId", customerId);
        draft.setAfterPreview(afterPreview);
        return draft;
    }

    private AgentDiagnosisActionDraftDto manualRecheckDraft() {
        AgentDiagnosisActionDraftDto draft = new AgentDiagnosisActionDraftDto();
        draft.setActionCode("CREATE_MANUAL_RECHECK_TASK");
        draft.setTitle("创建人工复核任务");
        draft.setRiskLevel("LOW");
        draft.setTargetType("RECHECK_TASK");
        draft.setTargetId("2026-07-08|LUNCH");
        draft.setRequiredPermission("agentDiagnosis:confirm");
        return draft;
    }

    private AgentDiagnosisActionDraftDto resumeDeliveryDraft(String recordDate, String mealType, Long customerId) {
        AgentDiagnosisActionDraftDto draft = new AgentDiagnosisActionDraftDto();
        draft.setActionCode("RESUME_CUSTOMER_DELIVERY");
        draft.setTitle("恢复客户配送");
        draft.setRiskLevel("MEDIUM");
        draft.setTargetType("CUSTOMER");
        draft.setTargetId(customerId == null ? null : String.valueOf(customerId));
        draft.setRequiredPermission("customerProfile:edit");
        Map<String, Object> afterPreview = new LinkedHashMap<>();
        afterPreview.put("recordDate", recordDate);
        afterPreview.put("mealType", mealType);
        afterPreview.put("customerId", customerId);
        draft.setAfterPreview(afterPreview);
        return draft;
    }

    private AgentDiagnosisActionDraftDto orderEffectiveDateDraft(Long orderId, String newStartDate, String newEndDate) {
        AgentDiagnosisActionDraftDto draft = new AgentDiagnosisActionDraftDto();
        draft.setActionCode("ADJUST_ORDER_EFFECTIVE_DATE");
        draft.setTitle("调整订单有效期");
        draft.setRiskLevel("HIGH");
        draft.setTargetType("ORDER");
        draft.setTargetId(orderId == null ? null : String.valueOf(orderId));
        draft.setRequiredPermission("customerOrder:edit");
        Map<String, Object> afterPreview = new LinkedHashMap<>();
        afterPreview.put("orderId", orderId);
        afterPreview.put("newStartDate", newStartDate);
        afterPreview.put("newEndDate", newEndDate);
        draft.setAfterPreview(afterPreview);
        return draft;
    }

    private AgentDiagnosisActionDraftDto orderBalanceDraft(Long orderId) {
        AgentDiagnosisActionDraftDto draft = new AgentDiagnosisActionDraftDto();
        draft.setActionCode("RECALCULATE_ORDER_BALANCE");
        draft.setTitle("重算订单剩余餐数");
        draft.setRiskLevel("HIGH");
        draft.setTargetType("ORDER");
        draft.setTargetId(orderId == null ? null : String.valueOf(orderId));
        draft.setRequiredPermission("customerOrder:edit");
        Map<String, Object> afterPreview = new LinkedHashMap<>();
        afterPreview.put("orderId", orderId);
        draft.setAfterPreview(afterPreview);
        return draft;
    }

    private AgentDiagnosisActionDraftDto confirmOnlyDraft(String actionCode, String title, String riskLevel, String targetType, String targetId) {
        AgentDiagnosisActionDraftDto draft = new AgentDiagnosisActionDraftDto();
        draft.setActionCode(actionCode);
        draft.setTitle(title);
        draft.setRiskLevel(riskLevel);
        draft.setTargetType(targetType);
        draft.setTargetId(targetId);
        draft.setRequiredPermission(requiredPermission(actionCode));
        Map<String, Object> afterPreview = new LinkedHashMap<>();
        afterPreview.put("reviewRequired", true);
        draft.setAfterPreview(afterPreview);
        return draft;
    }

    private String requiredPermission(String actionCode) {
        if ("CREATE_CUSTOMER_PROFILE_DRAFT".equals(actionCode)) {
            return "customerProfile:add";
        }
        if ("CREATE_CUSTOMER_ORDER_DRAFT".equals(actionCode)) {
            return "customerOrder:add";
        }
        if ("SUPPLEMENT_DISH_CANDIDATES".equals(actionCode)) {
            return "dish:edit";
        }
        return "agentDiagnosis:confirm";
    }
}
