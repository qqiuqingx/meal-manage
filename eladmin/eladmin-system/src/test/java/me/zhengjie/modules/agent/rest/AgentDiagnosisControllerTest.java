package me.zhengjie.modules.agent.rest;

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
        AgentDiagnosisFacadeService service = request -> {
            AgentDiagnosisResponse response = new AgentDiagnosisResponse();
            response.setCustomerId(request.getCustomerId());
            response.setSummary("AI 诊断结果");
            return response;
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
}
