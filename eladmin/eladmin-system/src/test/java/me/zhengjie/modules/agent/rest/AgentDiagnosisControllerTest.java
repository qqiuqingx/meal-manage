package me.zhengjie.modules.agent.rest;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

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
                return response;
            }

            @Override
            public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
                return new AgentChatResponse();
            }
        };
        AgentDiagnosisController controller = new AgentDiagnosisController(service);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        ResponseEntity<AgentDiagnosisResponse> response = controller.diagnoseMealPlan(request);

        assertNotNull(response.getBody());
        assertEquals(1001L, response.getBody().getCustomerId());
        assertEquals("AI 诊断结果", response.getBody().getSummary());
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
                AgentChatResponse response = new AgentChatResponse();
                response.setRequestId(requestId);
                response.setSessionId("session-1");
                response.setStatus("NEED_MORE_INFO");
                response.setAssistantMessage("请补充餐次：早餐、午餐还是晚餐？");
                return response;
            }
        };
        AgentDiagnosisController controller = new AgentDiagnosisController(service);

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("查客户 C10001 今天");

        ResponseEntity<AgentChatResponse> response = controller.chatMealPlan("request-1", request);

        assertNotNull(response.getBody());
        assertEquals("request-1", response.getBody().getRequestId());
        assertEquals("session-1", response.getBody().getSessionId());
        assertEquals("NEED_MORE_INFO", response.getBody().getStatus());
        assertEquals("请补充餐次：早餐、午餐还是晚餐？", response.getBody().getAssistantMessage());
    }
}
