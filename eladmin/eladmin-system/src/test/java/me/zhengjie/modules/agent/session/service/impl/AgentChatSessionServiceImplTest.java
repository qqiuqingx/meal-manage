package me.zhengjie.modules.agent.session.service.impl;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.modules.agent.mapper.AgentActionAuditMapper;
import me.zhengjie.modules.agent.mapper.AgentDiagnosisFeedbackMapper;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import me.zhengjie.modules.agent.service.AgentBusinessQueryAuditService;
import me.zhengjie.modules.agent.security.AgentAccessContextService;
import me.zhengjie.modules.agent.session.domain.AgentChatMessage;
import me.zhengjie.modules.agent.session.domain.AgentChatSession;
import me.zhengjie.modules.agent.session.mapper.AgentChatMessageMapper;
import me.zhengjie.modules.agent.session.mapper.AgentChatSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatSessionServiceImplTest {

    @Mock
    private AgentChatSessionMapper sessionMapper;

    @Mock
    private AgentChatMessageMapper messageMapper;

    @Mock
    private AgentActionAuditMapper actionAuditMapper;

    @Mock
    private AgentDiagnosisFeedbackMapper feedbackMapper;

    @Mock
    private AgentDiagnosisFacadeService diagnosisFacadeService;

    @Mock
    private AgentAccessContextService accessContextService;

    @Mock
    private AgentBusinessQueryAuditService businessQueryAuditService;

    @InjectMocks
    private AgentChatSessionServiceImpl service;

    @Test
    void shouldCreateSessionAndPersistUserAssistantMessagesWhenSessionIdMissing() {
        when(sessionMapper.insert(any(AgentChatSession.class))).thenAnswer(invocation -> {
            AgentChatSession session = invocation.getArgument(0);
            session.setId(1L);
            return 1;
        });
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(AgentChatMessage.class))).thenAnswer(invocation -> {
            AgentChatMessage message = invocation.getArgument(0);
            message.setId(1L);
            return 1;
        });
        when(sessionMapper.updateById(any(AgentChatSession.class))).thenReturn(1);

        AgentChatResponse facadeResponse = new AgentChatResponse();
        facadeResponse.setRequestId("req-1");
        facadeResponse.setSessionId("session-1");
        facadeResponse.setStatus("ANSWERED");
        facadeResponse.setAssistantMessage("已完成诊断");
        facadeResponse.setConversationStage("DIAGNOSED");
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode("C10001");
        slots.setRecordDate("2026-07-08");
        slots.setMealType("LUNCH");
        facadeResponse.setSlots(slots);
        AgentDiagnosisResponse diagnosisResponse = new AgentDiagnosisResponse();
        diagnosisResponse.setSummary("命中客户排除日期");
        diagnosisResponse.setCustomerId(1001L);
        diagnosisResponse.setRecordDate("2026-07-08");
        diagnosisResponse.setMealType("LUNCH");
        facadeResponse.setDiagnosisResult(diagnosisResponse);
        when(accessContextService.issue(any(), any())).thenReturn("signed-context");
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any())).thenReturn(facadeResponse);

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("查 C10001 今天午餐");

        AgentChatResponse response = service.chat(request, "req-1");

        assertEquals("session-1", response.getSessionId());
        assertEquals("req-1", response.getRequestId());
        assertEquals("DIAGNOSED", response.getConversationStage());

        ArgumentCaptor<AgentChatSession> sessionCaptor = ArgumentCaptor.forClass(AgentChatSession.class);
        verify(sessionMapper).insert(sessionCaptor.capture());
        verify(sessionMapper).updateById(any(AgentChatSession.class));
        verify(messageMapper, times(2)).insert(any(AgentChatMessage.class));
        verify(diagnosisFacadeService).chatMealPlan(any(AgentChatRequest.class), any(), any());
    }

    @Test
    void shouldReplayAssistantResponseWhenClientMessageIdAlreadyExists() {
        AgentChatSession session = new AgentChatSession();
        session.setId(1L);
        session.setSessionId("session-1");
        session.setOperator("system");
        session.setArchived(false);
        when(sessionMapper.selectOne(any())).thenReturn(session);

        AgentChatMessage existingUserMessage = new AgentChatMessage();
        existingUserMessage.setId(11L);
        existingUserMessage.setSessionId("session-1");
        existingUserMessage.setRequestId("req-2");
        existingUserMessage.setClientMessageId("msg-1");
        existingUserMessage.setRole("USER");

        AgentChatMessage assistantMessage = new AgentChatMessage();
        assistantMessage.setId(12L);
        assistantMessage.setSessionId("session-1");
        assistantMessage.setRequestId("req-2");
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setStatus("ANSWERED");
        assistantMessage.setConversationStage("DIAGNOSED");
        assistantMessage.setContent("已完成诊断");
        assistantMessage.setSlotsJson("{\"customerCode\":\"C10001\"}");
        assistantMessage.setDiagnosisResultJson("{\"summary\":\"命中客户排除日期\"}");
        assistantMessage.setBusinessResultJson("{\"responseType\":\"BUSINESS_QUERY_ORDER\",\"insightResult\":{\"total\":2},\"facts\":[{\"factId\":\"F1\",\"label\":\"订单数量\",\"value\":2,\"unit\":\"笔\"}],\"warnings\":[],\"partial\":false,\"queriedAt\":\"2026-07-11T10:00:00+08:00\",\"queryPlan\":{\"domain\":\"ORDER\",\"action\":\"LIST\"},\"pendingBusinessQueryContext\":{\"missingFields\":[\"recordDate\"]},\"lastBusinessQueryContext\":{\"metric\":\"MEAL_BALANCE\"},\"semanticTraceSummary\":{\"semanticSource\":\"PENDING_CONTEXT\",\"pendingContextReused\":true}}");
        assistantMessage.setCreateTime(new Timestamp(System.currentTimeMillis()));

        when(messageMapper.selectOne(any())).thenReturn(existingUserMessage, assistantMessage);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId("session-1");
        request.setClientMessageId("msg-1");
        request.setMessage("查 C10001 今天午餐");

        AgentChatResponse response = service.chat(request, "req-2");

        assertEquals("req-2", response.getRequestId());
        assertEquals("session-1", response.getSessionId());
        assertEquals("ANSWERED", response.getStatus());
        assertEquals("已完成诊断", response.getAssistantMessage());
        assertNotNull(response.getDiagnosisResult());
        assertEquals("BUSINESS_QUERY_ORDER", response.getResponseType());
        assertEquals(2, response.getInsightResult().get("total"));
        assertEquals("F1", response.getFacts().get(0).get("factId"));
        assertEquals("ORDER", response.getQueryPlan().get("domain"));
        assertEquals(Collections.singletonList("recordDate"), response.getPendingBusinessQueryContext().get("missingFields"));
        assertEquals("MEAL_BALANCE", response.getLastBusinessQueryContext().get("metric"));
        assertEquals("PENDING_CONTEXT", response.getSemanticTraceSummary().get("semanticSource"));
        verify(diagnosisFacadeService, never()).chatMealPlan(any(AgentChatRequest.class), any());
        verify(messageMapper, never()).insert(any(AgentChatMessage.class));
    }

    @Test
    void shouldClearOrderFocusWhenResponseSwitchesCustomer() {
        AgentChatSession session = new AgentChatSession();
        session.setId(1L);
        session.setSessionId("session-1");
        session.setOperator("system");
        session.setArchived(false);
        session.setCustomerId(1001L);
        session.setCustomerCode("C10001");
        session.setOrderId(2001L);
        session.setOrderCode("O20260001");
        session.setMealPlanRecordId(3001L);
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(AgentChatMessage.class))).thenReturn(1);
        when(sessionMapper.updateById(any(AgentChatSession.class))).thenReturn(1);
        when(accessContextService.issue(any(), any())).thenReturn("signed-context");

        AgentChatResponse facadeResponse = new AgentChatResponse();
        facadeResponse.setSessionId("session-1");
        facadeResponse.setRequestId("req-3");
        facadeResponse.setStatus("ANSWERED");
        facadeResponse.setAssistantMessage("客户已切换");
        facadeResponse.setConversationStage("DIAGNOSED");
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerId(1002L);
        slots.setCustomerCode("C10002");
        facadeResponse.setSlots(slots);
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any())).thenReturn(facadeResponse);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId("session-1");
        request.setMessage("改查客户 C10002");
        service.chat(request, "req-3");

        ArgumentCaptor<AgentChatSession> captor = ArgumentCaptor.forClass(AgentChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertEquals(1002L, captor.getValue().getCustomerId());
        assertEquals("C10002", captor.getValue().getCustomerCode());
        assertNull(captor.getValue().getOrderId());
        assertNull(captor.getValue().getOrderCode());
        assertNull(captor.getValue().getMealPlanRecordId());
    }

    @Test
    void shouldClearAllPersistedFocusWhenAgentResetsConversation() {
        AgentChatSession session = new AgentChatSession();
        session.setId(1L); session.setSessionId("session-1"); session.setOperator("system"); session.setArchived(false);
        session.setCustomerId(1001L); session.setCustomerCode("C10001"); session.setOrderId(2001L); session.setOrderCode("O20260001");
        session.setMealPlanRecordId(3001L); session.setRecordDate("2026-07-11"); session.setMealType("LUNCH");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(AgentChatMessage.class))).thenReturn(1);
        when(sessionMapper.updateById(any(AgentChatSession.class))).thenReturn(1);
        when(accessContextService.issue(any(), any())).thenReturn("signed-context");
        AgentChatResponse reset = new AgentChatResponse();
        reset.setSessionId("session-1"); reset.setRequestId("req-reset"); reset.setStatus("RESET");
        reset.setAssistantMessage("会话已清空"); reset.setConversationStage("RESET"); reset.setSlots(new DiagnosisSlots());
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any())).thenReturn(reset);
        AgentChatRequest request = new AgentChatRequest(); request.setSessionId("session-1"); request.setMessage("清空会话");

        service.chat(request, "req-reset");

        ArgumentCaptor<AgentChatSession> captor = ArgumentCaptor.forClass(AgentChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        AgentChatSession updated = captor.getValue();
        assertNull(updated.getCustomerId()); assertNull(updated.getCustomerCode()); assertNull(updated.getOrderId());
        assertNull(updated.getOrderCode()); assertNull(updated.getMealPlanRecordId()); assertNull(updated.getRecordDate()); assertNull(updated.getMealType());
    }

    @Test
    void shouldPersistDateRangeAndClearSingleDateFocus() {
        AgentChatSession session = new AgentChatSession();
        session.setId(1L); session.setSessionId("session-1"); session.setOperator("system"); session.setArchived(false);
        session.setRecordDate("2026-07-11");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(AgentChatMessage.class))).thenReturn(1);
        when(sessionMapper.updateById(any(AgentChatSession.class))).thenReturn(1);
        when(accessContextService.issue(any(), any())).thenReturn("signed-context");
        AgentChatResponse rangeResponse = new AgentChatResponse();
        rangeResponse.setSessionId("session-1"); rangeResponse.setRequestId("req-range"); rangeResponse.setStatus("ANSWERED");
        rangeResponse.setAssistantMessage("已查询本月退款"); rangeResponse.setConversationStage("DIAGNOSED");
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setStartDate("2026-07-01"); slots.setEndDate("2026-07-11");
        rangeResponse.setSlots(slots);
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any())).thenReturn(rangeResponse);

        AgentChatRequest request = new AgentChatRequest(); request.setSessionId("session-1"); request.setMessage("查本月退款");
        service.chat(request, "req-range");

        ArgumentCaptor<AgentChatSession> captor = ArgumentCaptor.forClass(AgentChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        AgentChatSession updated = captor.getValue();
        assertNull(updated.getRecordDate());
        assertEquals("2026-07-01", updated.getQueryStartDate());
        assertEquals("2026-07-11", updated.getQueryEndDate());
    }

    /** 从数据库重新读取会话后，下一实例必须将完整受控业务焦点下发给 Agent。 */
    @Test
    void shouldRestorePersistedBusinessFocusForNextChatRequest() {
        AgentChatSession session = new AgentChatSession();
        session.setId(1L); session.setSessionId("session-recovered"); session.setOperator("system"); session.setArchived(false);
        session.setCustomerId(1001L); session.setCustomerCode("C10001");
        session.setOrderId(2001L); session.setOrderCode("O20260001"); session.setMealPlanRecordId(3001L);
        session.setRecordDate("2026-07-13"); session.setQueryStartDate("2026-07-01"); session.setQueryEndDate("2026-07-13"); session.setMealType("LUNCH");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(AgentChatMessage.class))).thenReturn(1);
        when(sessionMapper.updateById(any(AgentChatSession.class))).thenReturn(1);
        when(accessContextService.issue(any(), any())).thenReturn("signed-context");
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-recovered"); response.setRequestId("req-recovered"); response.setStatus("ANSWERED");
        response.setAssistantMessage("已继续查询"); response.setConversationStage("DIAGNOSED"); response.setSlots(new DiagnosisSlots());
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any())).thenReturn(response);

        AgentChatRequest request = new AgentChatRequest(); request.setSessionId("session-recovered"); request.setMessage("这笔订单呢");
        service.chat(request, "req-recovered");

        ArgumentCaptor<AgentChatRequest> captured = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(diagnosisFacadeService).chatMealPlan(captured.capture(), any(), any());
        DiagnosisSlots slots = captured.getValue().getContextSlots();
        assertEquals(1001L, slots.getCustomerId()); assertEquals("C10001", slots.getCustomerCode());
        assertEquals(2001L, slots.getOrderId()); assertEquals("O20260001", slots.getOrderCode());
        assertEquals(3001L, slots.getMealPlanRecordId()); assertEquals("2026-07-13", slots.getRecordDate());
        assertEquals("2026-07-01", slots.getStartDate()); assertEquals("2026-07-13", slots.getEndDate()); assertEquals("LUNCH", slots.getMealType());
    }

    /** Pending/Last Context 必须从数据库下发并用本轮 Agent 响应回写，支持重启和实例切换。 */
    @Test
    void shouldRoundTripPersistedSemanticContextsAcrossAgentInstances() {
        AgentChatSession session = new AgentChatSession();
        session.setId(1L); session.setSessionId("semantic-session"); session.setOperator("system"); session.setArchived(false);
        session.setPendingBusinessQueryJson("{\"missingFields\":[\"recordDate\"],\"originalQuestionSummary\":\"待排餐客户数\"}");
        session.setLastBusinessQueryContextJson("{\"metric\":\"DAILY_SCHEDULED_CUSTOMER_COUNT\",\"recordDate\":\"2026-07-13\"}");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(AgentChatMessage.class))).thenReturn(1);
        when(sessionMapper.updateById(any(AgentChatSession.class))).thenReturn(1);
        when(accessContextService.issue(any(), any())).thenReturn("signed-context");
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("semantic-session"); response.setRequestId("semantic-request"); response.setStatus("ANSWERED");
        response.setAssistantMessage("已继续查询"); response.setConversationStage("DIAGNOSED"); response.setSlots(new DiagnosisSlots());
        response.setPendingBusinessQueryContext(null);
        response.setLastBusinessQueryContext(Map.of("metric", "DAILY_UNSCHEDULED_CUSTOMER_COUNT", "recordDate", "2026-07-14"));
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any())).thenReturn(response);

        AgentChatRequest request = new AgentChatRequest(); request.setSessionId("semantic-session"); request.setMessage("今天");
        service.chat(request, "semantic-request");

        ArgumentCaptor<AgentChatRequest> downstream = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(diagnosisFacadeService).chatMealPlan(downstream.capture(), any(), any());
        assertEquals(Collections.singletonList("recordDate"), downstream.getValue().getPendingBusinessQueryContext().get("missingFields"));
        assertEquals("DAILY_SCHEDULED_CUSTOMER_COUNT", downstream.getValue().getLastBusinessQueryContext().get("metric"));
        ArgumentCaptor<AgentChatSession> updated = ArgumentCaptor.forClass(AgentChatSession.class);
        verify(sessionMapper).updateById(updated.capture());
        assertNull(updated.getValue().getPendingBusinessQueryJson());
        assertTrue(updated.getValue().getLastBusinessQueryContextJson().contains("DAILY_UNSCHEDULED_CUSTOMER_COUNT"));
    }

    /** 两个 Agent 实例交替请求时，受控任务栈必须从主系统恢复并由下一轮回写。 */
    @Test
    void shouldRoundTripPersistedTaskStackAcrossAgentInstances() {
        AgentChatSession session = new AgentChatSession();
        session.setId(1L); session.setSessionId("task-stack-session"); session.setOperator("system"); session.setArchived(false);
        session.setActiveTaskStackJson("{\"tasks\":[{\"taskId\":\"task-1\",\"status\":\"SUSPENDED\"}]}");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(AgentChatMessage.class))).thenReturn(1);
        when(sessionMapper.updateById(any(AgentChatSession.class))).thenReturn(1);
        when(accessContextService.issue(any(), any())).thenReturn("signed-context");
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("task-stack-session"); response.setRequestId("task-stack-request"); response.setStatus("ANSWERED");
        response.setAssistantMessage("已恢复原任务"); response.setConversationStage("DIAGNOSED"); response.setSlots(new DiagnosisSlots());
        response.setActiveTaskStack(Map.of("tasks", Collections.singletonList(Map.of("taskId", "task-1", "status", "ACTIVE"))));
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any())).thenReturn(response);

        AgentChatRequest request = new AgentChatRequest(); request.setSessionId("task-stack-session"); request.setMessage("继续原来的查询");
        service.chat(request, "task-stack-request");

        ArgumentCaptor<AgentChatRequest> downstream = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(diagnosisFacadeService).chatMealPlan(downstream.capture(), any(), any());
        assertEquals("SUSPENDED", ((Map<?, ?>) ((java.util.List<?>) downstream.getValue().getActiveTaskStack().get("tasks")).get(0)).get("status"));
        ArgumentCaptor<AgentChatSession> updated = ArgumentCaptor.forClass(AgentChatSession.class);
        verify(sessionMapper).updateById(updated.capture());
        assertTrue(updated.getValue().getActiveTaskStackJson().contains("ACTIVE"));
    }
}
