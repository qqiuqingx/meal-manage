package me.zhengjie.modules.agent.session.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionQueryCriteria;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionSummaryDto;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    /** 新消息快照必须无损保存 cards 与 presentations 的关联、来源和列顺序。 */
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
        facadeResponse.setConversationStage("ANSWERED");
        Map<String, Object> presentation = new LinkedHashMap<>();
        presentation.put("schemaVersion", "v1");
        presentation.put("sourceToolCallId", "call-1");
        presentation.put("cardType", "SERVICE_CUSTOMER_LIST");
        presentation.put("decisionSource", "SYSTEM");
        presentation.put("title", "服务客户下单明细");
        presentation.put("layout", "TABS");
        presentation.put("defaultView", "TABLE");
        presentation.put("availableViews", Collections.singletonList("TABLE"));
        presentation.put("table", Collections.singletonMap("columns", List.of(
            Collections.singletonMap("field", "customerCode"),
            Collections.singletonMap("field", "customerName"),
            Collections.singletonMap("field", "orderCode"),
            Collections.singletonMap("field", "orderTime"),
            Collections.singletonMap("field", "status"),
            Collections.singletonMap("field", "parentPackageName"))));
        facadeResponse.setPresentations(Collections.singletonList(presentation));
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
        assertEquals("ANSWERED", response.getConversationStage());

        ArgumentCaptor<AgentChatSession> sessionCaptor = ArgumentCaptor.forClass(AgentChatSession.class);
        verify(sessionMapper).insert(sessionCaptor.capture());
        verify(sessionMapper).updateById(any(AgentChatSession.class));
        ArgumentCaptor<AgentChatMessage> messageCaptor = ArgumentCaptor.forClass(AgentChatMessage.class);
        verify(messageMapper, times(2)).insert(messageCaptor.capture());
        AgentChatMessage persistedAssistant = messageCaptor.getAllValues().stream()
            .filter(message -> "ASSISTANT".equals(message.getRole()))
            .findFirst().orElseThrow(AssertionError::new);
        Map<String, Object> businessSnapshot = JSON.parseObject(persistedAssistant.getBusinessResultJson(), Map.class);
        Map<String, Object> persistedPresentation = ((List<Map<String, Object>>) businessSnapshot.get("presentations"))
            .get(0);
        assertEquals("v1", persistedPresentation.get("schemaVersion"));
        assertEquals("call-1", persistedPresentation.get("sourceToolCallId"));
        assertEquals("SYSTEM", persistedPresentation.get("decisionSource"));
        assertEquals("TABLE", persistedPresentation.get("defaultView"));
        List<Map<String, Object>> persistedColumns = (List<Map<String, Object>>)
            ((Map<String, Object>) persistedPresentation.get("table")).get("columns");
        assertEquals(List.of("customerCode", "customerName", "orderCode", "orderTime", "status", "parentPackageName"),
            persistedColumns.stream().map(column -> (String) column.get("field")).collect(java.util.stream.Collectors.toList()));
        verify(diagnosisFacadeService).chatMealPlan(any(AgentChatRequest.class), any(), any());
    }

    /** 数据库版本条件更新失败时，本轮不能用过期 Agent 结论覆盖较新的会话快照。 */
    @Test
    void shouldRejectSessionVersionConflict() {
        AgentChatSession session = new AgentChatSession();
        session.setId(1L); session.setSessionId("session-conflict"); session.setOperator("system"); session.setArchived(false); session.setVersion(3);
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(AgentChatMessage.class))).thenReturn(1);
        when(accessContextService.issue(any(), any())).thenReturn("signed-context");
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-conflict"); response.setStatus("ANSWERED"); response.setAssistantMessage("完成");
        response.setConversationStage("ANSWERED"); response.setSlots(new DiagnosisSlots()); response.setExpectedSessionVersion(3L);
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any())).thenReturn(response);
        when(sessionMapper.updateById(any(AgentChatSession.class))).thenReturn(0);

        AgentChatRequest request = new AgentChatRequest(); request.setSessionId("session-conflict"); request.setMessage("查询订单");
        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.chat(request, "req-conflict"));
        assertTrue(exception.getMessage().contains("SESSION_VERSION_CONFLICT"));
        verify(messageMapper, never()).insert(org.mockito.ArgumentMatchers.argThat(message -> "ASSISTANT".equals(message.getRole())));
    }

    @Test
    /** 命中旧消息快照时保留原展示决策，且不重新调用 Agent 或业务审计链路。 */
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
        assistantMessage.setConversationStage("ANSWERED");
        assistantMessage.setContent("已完成诊断");
        assistantMessage.setSlotsJson("{\"customerCode\":\"C10001\"}");
        assistantMessage.setDiagnosisResultJson("{\"summary\":\"命中客户排除日期\"}");
        assistantMessage.setBusinessResultJson("{\"cards\":[{\"type\":\"SERVICE_CUSTOMER_LIST\",\"sourceToolCallId\":\"call-1\",\"data\":{\"total\":2,\"maskedName\":\"历史掩码\"}}],\"presentations\":[{\"schemaVersion\":\"v1\",\"sourceToolCallId\":\"call-1\",\"cardType\":\"SERVICE_CUSTOMER_LIST\",\"decisionSource\":\"SYSTEM\",\"title\":\"客户订单\",\"layout\":\"TABS\",\"defaultView\":\"TABLE\",\"availableViews\":[\"TABLE\"],\"table\":{\"dataPath\":\"items\",\"columns\":[{\"field\":\"customerCode\",\"label\":\"客户编号\",\"format\":\"TEXT\"}]}}],\"toolFacts\":[{\"callId\":\"call-1\",\"toolName\":\"searchServiceCustomers\"}],\"toolTraceSummary\":[{\"toolName\":\"searchServiceCustomers\",\"status\":\"SUCCESS\"}],\"warnings\":[],\"partial\":false,\"queriedAt\":\"2026-07-11T10:00:00+08:00\",\"lastBusinessQueryContext\":{\"toolName\":\"searchServiceCustomers\"}}");
        assistantMessage.setCreateTime(new Timestamp(System.currentTimeMillis()));

        when(messageMapper.selectOne(any())).thenReturn(existingUserMessage, assistantMessage);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId("session-1");
        request.setClientMessageId("msg-1");
        request.setMessage("查 C10001 今天午餐");

        AgentChatResponse response = service.chat(request, "req-2");

        assertEquals("req-2", response.getRequestId());
        assertEquals("session-1", response.getSessionId());
        assertEquals("msg-1", response.getClientMessageId());
        assertEquals("ANSWERED", response.getStatus());
        assertEquals("已完成诊断", response.getAssistantMessage());
        assertNotNull(response.getDiagnosisResult());
        assertEquals("SERVICE_CUSTOMER_LIST", response.getCards().get(0).get("type"));
        assertEquals("历史掩码", ((java.util.Map<String, Object>) response.getCards().get(0).get("data")).get("maskedName"));
        assertEquals("v1", response.getPresentations().get(0).get("schemaVersion"));
        assertEquals("customerCode", ((java.util.List<java.util.Map<String, Object>>)
            ((java.util.Map<String, Object>) response.getPresentations().get(0).get("table")).get("columns"))
            .get(0).get("field"));
        assertEquals("searchServiceCustomers", response.getToolFacts().get(0).get("toolName"));
        assertEquals("searchServiceCustomers", response.getLastBusinessQueryContext().get("toolName"));
        verify(sessionMapper).selectBySessionIdForUpdate("session-1");
        verify(diagnosisFacadeService, never()).chatMealPlan(
            any(AgentChatRequest.class), any(), any());
        verify(accessContextService, never()).issue(any(), any());
        verify(businessQueryAuditService, never()).record(any(), any(), org.mockito.ArgumentMatchers.anyLong());
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
        facadeResponse.setConversationStage("ANSWERED");
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
        rangeResponse.setAssistantMessage("已查询本月退款"); rangeResponse.setConversationStage("ANSWERED");
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
        response.setAssistantMessage("已继续查询"); response.setConversationStage("ANSWERED"); response.setSlots(new DiagnosisSlots());
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

    /** Agent 返回 Patch 时，主系统必须优先使用 Patch 槽位和最近查询摘要，而不是旧顶层字段。 */
    @Test
    void shouldPreferConversationPatchWhenPersistingSessionFocus() {
        AgentChatSession session = new AgentChatSession();
        session.setId(1L); session.setSessionId("session-patch"); session.setOperator("system"); session.setArchived(false);
        session.setCustomerId(1001L); session.setCustomerCode("C10001");
        session.setOrderId(2001L); session.setOrderCode("O-OLD"); session.setMealPlanRecordId(3001L);
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(AgentChatMessage.class))).thenReturn(1);
        when(sessionMapper.updateById(any(AgentChatSession.class))).thenReturn(1);
        when(accessContextService.issue(any(), any())).thenReturn("signed-context");

        DiagnosisSlots topLevelSlots = new DiagnosisSlots();
        topLevelSlots.setCustomerCode("C99999");
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-patch"); response.setRequestId("req-patch"); response.setStatus("ANSWERED");
        response.setAssistantMessage("已按新客户查询"); response.setConversationStage("ANSWERED");
        response.setSlots(topLevelSlots);
        response.setLastBusinessQueryContext(Collections.singletonMap("lastToolName", "oldTool"));
        Map<String, Object> patchSlots = new LinkedHashMap<>();
        patchSlots.put("customerId", 1002L);
        patchSlots.put("customerCode", "C10002");
        patchSlots.put("recordDate", "2026-08-09");
        patchSlots.put("mealType", "DINNER");
        Map<String, Object> patchSummary = new LinkedHashMap<>();
        patchSummary.put("lastToolName", "listMealPlans");
        patchSummary.put("successfulToolNames", Collections.singletonList("listMealPlans"));
        response.setConversationPatch(new LinkedHashMap<>(Map.of(
            "slots", patchSlots,
            "conversationStage", "ANSWERED",
            "lastBusinessQueryContext", patchSummary)));
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any())).thenReturn(response);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId("session-patch"); request.setMessage("查 C10002 今天晚餐");
        AgentChatResponse result = service.chat(request, "req-patch");

        ArgumentCaptor<AgentChatSession> captor = ArgumentCaptor.forClass(AgentChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        AgentChatSession updated = captor.getValue();
        assertEquals(1002L, updated.getCustomerId());
        assertEquals("C10002", updated.getCustomerCode());
        assertNull(updated.getOrderId());
        assertNull(updated.getOrderCode());
        assertNull(updated.getMealPlanRecordId());
        assertEquals("2026-08-09", updated.getRecordDate());
        assertEquals("DINNER", updated.getMealType());
        assertTrue(updated.getLastBusinessQueryContextJson().contains("listMealPlans"));
        assertEquals("C10002", result.getSlots().getCustomerCode());
        assertEquals("listMealPlans", result.getLastBusinessQueryContext().get("lastToolName"));
    }

    /** 澄清回合没有业务卡片时也必须写入现有业务快照，并能从历史快照恢复协议字段。 */
    @Test
    void shouldPersistAndRestoreClarificationProtocolFields() {
        AgentChatResponse response = new AgentChatResponse();
        response.setStatus("NEED_MORE_INFO");
        response.setAssistantMessage("请补充餐次。");
        response.setMissingSlots(List.of("MEAL_TYPE"));
        response.setQuickReplies(List.of("早餐", "午餐", "晚餐"));

        Map<String, Object> snapshot = ReflectionTestUtils.invokeMethod(service,
            "buildBusinessSnapshot", response);
        assertNotNull(snapshot);
        assertEquals(List.of("MEAL_TYPE"), snapshot.get("missingSlots"));
        assertEquals(List.of("早餐", "午餐", "晚餐"), snapshot.get("quickReplies"));

        AgentChatResponse restored = new AgentChatResponse();
        ReflectionTestUtils.invokeMethod(service, "restoreBusinessResponse", restored, snapshot);

        assertEquals(List.of("MEAL_TYPE"), restored.getMissingSlots());
        assertEquals(List.of("早餐", "午餐", "晚餐"), restored.getQuickReplies());
    }

    @Test
    /** 会话列表必须按页返回当前客服可见摘要，并使用进行中视图作为默认归档条件。 */
    void shouldQueryPagedSessionSummariesWithKeyword() {
        when(sessionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<AgentChatSession> page = invocation.getArgument(0);
            assertEquals(2L, page.getCurrent());
            assertEquals(20L, page.getSize());
            AgentChatSession session = new AgentChatSession();
            session.setSessionId("session-21");
            session.setTitle("C10001 午餐排查");
            session.setCustomerCode("C10001");
            session.setLastSummary("已完成查询");
            session.setArchived(false);
            page.setRecords(Collections.singletonList(session));
            page.setTotal(25L);
            return page;
        });

        AgentChatSessionQueryCriteria criteria = new AgentChatSessionQueryCriteria();
        criteria.setKeyword("  C10001  ");
        criteria.setPage(1);
        PageResult<AgentChatSessionSummaryDto> result = service.querySessions(criteria);

        assertEquals(25L, result.getTotalElements());
        assertEquals("session-21", result.getContent().get(0).getSessionId());
        assertEquals("C10001", result.getContent().get(0).getCustomerCode());
        assertEquals(Boolean.FALSE, criteria.getArchived());
    }

}
