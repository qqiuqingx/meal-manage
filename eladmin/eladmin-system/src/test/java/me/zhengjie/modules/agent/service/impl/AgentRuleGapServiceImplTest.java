package me.zhengjie.modules.agent.service.impl;

import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.AgentRuleGap;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapResponse;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapStatusRequest;
import me.zhengjie.modules.agent.mapper.AgentRuleGapMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRuleGapServiceImplTest {

    @Mock
    private AgentRuleGapMapper ruleGapMapper;

    @InjectMocks
    private AgentRuleGapServiceImpl service;

    @Test
    void shouldCreateRuleGapFromRejectedFeedbackWithUnknownReason() {
        when(ruleGapMapper.selectOne(any())).thenReturn(null);

        service.createFromFeedback(feedback("REJECTED", "NEW_REASON_CODE"));

        ArgumentCaptor<AgentRuleGap> captor = ArgumentCaptor.forClass(AgentRuleGap.class);
        verify(ruleGapMapper).insert(captor.capture());
        AgentRuleGap gap = captor.getValue();
        assertEquals("UNKNOWN_REASON", gap.getGapType());
        assertEquals("FEEDBACK", gap.getSourceType());
        assertEquals("NEW_REASON_CODE", gap.getActualReasonCode());
        assertEquals("OPEN", gap.getStatus());
        assertEquals(1, gap.getOccurrenceCount());
        assertNotNull(gap.getCreateTime());
    }

    @Test
    void shouldIncreaseOccurrenceWhenOpenGapAlreadyExists() {
        AgentRuleGap existing = new AgentRuleGap();
        existing.setId(10L);
        existing.setOccurrenceCount(2);
        when(ruleGapMapper.selectOne(any())).thenReturn(existing);

        service.createFromFeedback(feedback("PARTIAL", "ORDER_EXPIRED"));

        ArgumentCaptor<AgentRuleGap> captor = ArgumentCaptor.forClass(AgentRuleGap.class);
        verify(ruleGapMapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getOccurrenceCount());
        assertNotNull(captor.getValue().getUpdateTime());
        verify(ruleGapMapper, never()).insert(any());
    }

    @Test
    void shouldRejectInProgressWithoutOwner() {
        AgentRuleGapStatusRequest request = statusRequest("IN_PROGRESS", null, "开始处理");

        AgentRuleGapResponse response = service.updateStatus(1L, request);

        assertEquals("VALIDATION_FAILED", response.getStatus());
        assertEquals("规则缺口进入处理中必须指定处理人", response.getMessage());
        verify(ruleGapMapper, never()).selectById(any());
        verify(ruleGapMapper, never()).updateById(any());
    }

    @Test
    void shouldRejectResolvedWithoutMaintenanceEvidenceComment() {
        AgentRuleGapStatusRequest request = statusRequest("RESOLVED", "rule-admin", null);

        AgentRuleGapResponse response = service.updateStatus(1L, request);

        assertEquals("VALIDATION_FAILED", response.getStatus());
        assertEquals("规则缺口标记已解决必须填写规则或评测维护证据", response.getMessage());
        verify(ruleGapMapper, never()).selectById(any());
        verify(ruleGapMapper, never()).updateById(any());
    }

    @Test
    void shouldResolveGapWhenMaintenanceEvidenceProvided() {
        AgentRuleGap gap = new AgentRuleGap();
        gap.setId(1L);
        gap.setGapDescription("客服反馈=REJECTED");
        when(ruleGapMapper.selectById(1L)).thenReturn(gap);

        AgentRuleGapStatusRequest request = statusRequest("RESOLVED", "rule-admin", "已补充规则 ORDER_EXPIRED_V2 和评测用例 CASE-102");
        AgentRuleGapResponse response = service.updateStatus(1L, request);

        assertEquals("RESOLVED", response.getStatus());
        ArgumentCaptor<AgentRuleGap> captor = ArgumentCaptor.forClass(AgentRuleGap.class);
        verify(ruleGapMapper).updateById(captor.capture());
        assertEquals("RESOLVED", captor.getValue().getStatus());
        assertEquals("rule-admin", captor.getValue().getOwner());
        assertEquals("客服反馈=REJECTED；处理备注：已补充规则 ORDER_EXPIRED_V2 和评测用例 CASE-102", captor.getValue().getGapDescription());
    }

    @Test
    void shouldRejectUnknownStatus() {
        AgentRuleGapResponse response = service.updateStatus(1L, statusRequest("CLOSED", "rule-admin", "done"));

        assertEquals("VALIDATION_FAILED", response.getStatus());
        assertEquals("规则缺口状态不合法", response.getMessage());
        verify(ruleGapMapper, never()).selectById(any());
    }

    private AgentDiagnosisFeedback feedback(String accepted, String actualReasonCode) {
        AgentDiagnosisFeedback feedback = new AgentDiagnosisFeedback();
        feedback.setId(99L);
        feedback.setRequestId("diag-001");
        feedback.setCustomerId(1001L);
        feedback.setRecordDate("2026-07-08");
        feedback.setMealType("LUNCH");
        feedback.setPredictedReasonCodes("[\"ORDER_EXPIRED\"]");
        feedback.setAccepted(accepted);
        feedback.setActualReasonCode(actualReasonCode);
        feedback.setComment("客服反馈备注");
        return feedback;
    }

    private AgentRuleGapStatusRequest statusRequest(String status, String owner, String comment) {
        AgentRuleGapStatusRequest request = new AgentRuleGapStatusRequest();
        request.setStatus(status);
        request.setOwner(owner);
        request.setComment(comment);
        return request;
    }
}
