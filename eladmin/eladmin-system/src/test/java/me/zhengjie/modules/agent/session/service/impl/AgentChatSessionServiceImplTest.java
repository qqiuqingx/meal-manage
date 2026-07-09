package me.zhengjie.modules.agent.session.service.impl;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.modules.agent.mapper.AgentActionAuditMapper;
import me.zhengjie.modules.agent.mapper.AgentDiagnosisFeedbackMapper;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any())).thenReturn(facadeResponse);

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
        verify(diagnosisFacadeService).chatMealPlan(any(AgentChatRequest.class), any());
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
        verify(diagnosisFacadeService, never()).chatMealPlan(any(AgentChatRequest.class), any());
        verify(messageMapper, never()).insert(any(AgentChatMessage.class));
    }
}
