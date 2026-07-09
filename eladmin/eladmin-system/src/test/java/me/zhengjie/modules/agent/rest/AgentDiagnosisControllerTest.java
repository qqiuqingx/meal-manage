package me.zhengjie.modules.agent.rest;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionCreateRequest;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionDetailDto;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionQueryCriteria;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionSummaryDto;
import me.zhengjie.modules.agent.session.service.AgentChatSessionService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentDiagnosisControllerTest {

    @Test
    void shouldDelegateMealPlanDiagnosis() {
        AgentDiagnosisFacadeService service = new AgentDiagnosisFacadeService() {
            @Override
            public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
                AgentDiagnosisResponse response = new AgentDiagnosisResponse();
                response.setCustomerId(request.getCustomerId());
                response.setSummary("AI 诊断结果");
                response.setConfidence("HIGH");
                response.setFallbackReason(null);
                return response;
            }

            @Override
            public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
                return new AgentChatResponse();
            }
        };
        AgentChatSessionService chatSessionService = noOpChatSessionService();
        AgentDiagnosisController controller = new AgentDiagnosisController(service, chatSessionService);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        ResponseEntity<AgentDiagnosisResponse> response = controller.diagnoseMealPlan(request);

        assertNotNull(response.getBody());
        assertEquals(1001L, response.getBody().getCustomerId());
        assertEquals("AI 诊断结果", response.getBody().getSummary());
        assertEquals("HIGH", response.getBody().getConfidence());
    }

    @Test
    void shouldDelegateMealPlanChat() {
        AgentDiagnosisFacadeService service = new AgentDiagnosisFacadeService() {
            @Override
            public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
                return new AgentDiagnosisResponse();
            }

            @Override
            public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
                return new AgentChatResponse();
            }
        };
        AgentChatSessionService chatSessionService = new AgentChatSessionService() {
            @Override
            public AgentChatSessionSummaryDto createSession(AgentChatSessionCreateRequest request) {
                return new AgentChatSessionSummaryDto();
            }

            @Override
            public PageResult<AgentChatSessionSummaryDto> querySessions(AgentChatSessionQueryCriteria criteria) {
                return new PageResult<>(Collections.emptyList(), 0);
            }

            @Override
            public AgentChatSessionDetailDto getSession(String sessionId) {
                return new AgentChatSessionDetailDto();
            }

            @Override
            public void updateTitle(String sessionId, String title) {
            }

            @Override
            public void updateArchiveStatus(String sessionId, boolean archived) {
            }

            @Override
            public AgentChatResponse chat(AgentChatRequest request, String requestId) {
                AgentChatResponse response = new AgentChatResponse();
                response.setRequestId(requestId);
                response.setSessionId("session-1");
                response.setStatus("NEED_MORE_INFO");
                response.setAssistantMessage("请补充餐次：早餐、午餐还是晚餐？");
                response.setSlotConfidence(java.util.Map.of("customer", "HIGH"));
                response.setMissingSlots(java.util.List.of("MEAL_TYPE"));
                response.setConversationStage("COLLECTING_SLOTS");
                return response;
            }
        };
        AgentDiagnosisController controller = new AgentDiagnosisController(service, chatSessionService);

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("查客户 C10001 今天");

        ResponseEntity<AgentChatResponse> response = controller.chatMealPlan("request-1", request);

        assertNotNull(response.getBody());
        assertEquals("request-1", response.getBody().getRequestId());
        assertEquals("session-1", response.getBody().getSessionId());
        assertEquals("NEED_MORE_INFO", response.getBody().getStatus());
        assertEquals("请补充餐次：早餐、午餐还是晚餐？", response.getBody().getAssistantMessage());
        assertEquals("HIGH", response.getBody().getSlotConfidence().get("customer"));
        assertEquals("MEAL_TYPE", response.getBody().getMissingSlots().get(0));
        assertEquals("COLLECTING_SLOTS", response.getBody().getConversationStage());
    }

    private AgentChatSessionService noOpChatSessionService() {
        return new AgentChatSessionService() {
            @Override
            public AgentChatSessionSummaryDto createSession(AgentChatSessionCreateRequest request) {
                return new AgentChatSessionSummaryDto();
            }

            @Override
            public PageResult<AgentChatSessionSummaryDto> querySessions(AgentChatSessionQueryCriteria criteria) {
                return new PageResult<>(Collections.emptyList(), 0);
            }

            @Override
            public AgentChatSessionDetailDto getSession(String sessionId) {
                return new AgentChatSessionDetailDto();
            }

            @Override
            public void updateTitle(String sessionId, String title) {
            }

            @Override
            public void updateArchiveStatus(String sessionId, boolean archived) {
            }

            @Override
            public AgentChatResponse chat(AgentChatRequest request, String requestId) {
                return new AgentChatResponse();
            }
        };
    }
}
