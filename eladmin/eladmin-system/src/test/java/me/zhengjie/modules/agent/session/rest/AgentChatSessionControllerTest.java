package me.zhengjie.modules.agent.session.rest;

import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionCreateRequest;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionDetailDto;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionQueryCriteria;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionSummaryDto;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionTitleUpdateRequest;
import me.zhengjie.modules.agent.session.service.AgentChatSessionService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatSessionControllerTest {

    @Mock
    private AgentChatSessionService chatSessionService;

    @InjectMocks
    private AgentChatSessionController controller;

    @Test
    void shouldQuerySessionPage() {
        AgentChatSessionSummaryDto summary = new AgentChatSessionSummaryDto();
        summary.setSessionId("session-1");
        when(chatSessionService.querySessions(org.mockito.ArgumentMatchers.any(AgentChatSessionQueryCriteria.class)))
            .thenReturn(new PageResult<>(Collections.singletonList(summary), 1));

        AgentChatSessionQueryCriteria criteria = new AgentChatSessionQueryCriteria();
        criteria.setKeyword("C10001");

        ResponseEntity<PageResult<AgentChatSessionSummaryDto>> response = controller.query(criteria);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("session-1", response.getBody().getContent().get(0).getSessionId());
    }

    @Test
    void shouldCreateSession() {
        AgentChatSessionSummaryDto summary = new AgentChatSessionSummaryDto();
        summary.setSessionId("session-2");
        when(chatSessionService.createSession(org.mockito.ArgumentMatchers.any(AgentChatSessionCreateRequest.class))).thenReturn(summary);

        AgentChatSessionCreateRequest request = new AgentChatSessionCreateRequest();
        request.setTitle("午餐排查");

        ResponseEntity<AgentChatSessionSummaryDto> response = controller.create(request);

        assertNotNull(response.getBody());
        assertEquals("session-2", response.getBody().getSessionId());
    }

    @Test
    void shouldGetSessionDetail() {
        AgentChatSessionDetailDto detail = new AgentChatSessionDetailDto();
        detail.setSessionId("session-3");
        when(chatSessionService.getSession("session-3")).thenReturn(detail);

        ResponseEntity<AgentChatSessionDetailDto> response = controller.get("session-3");

        assertNotNull(response.getBody());
        assertEquals("session-3", response.getBody().getSessionId());
    }

    @Test
    void shouldArchiveSession() {
        ResponseEntity<Void> response = controller.archive("session-4", true);

        assertEquals(200, response.getStatusCodeValue());
        verify(chatSessionService).updateArchiveStatus("session-4", true);
    }

    @Test
    void shouldUpdateSessionTitle() {
        AgentChatSessionTitleUpdateRequest request = new AgentChatSessionTitleUpdateRequest();
        request.setTitle("新的会话标题");

        ResponseEntity<Void> response = controller.updateTitle("session-5", request);

        assertEquals(200, response.getStatusCodeValue());
        verify(chatSessionService).updateTitle("session-5", "新的会话标题");
    }
}
