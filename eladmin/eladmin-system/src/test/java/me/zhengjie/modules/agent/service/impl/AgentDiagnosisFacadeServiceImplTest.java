package me.zhengjie.modules.agent.service.impl;

import me.zhengjie.modules.agent.client.AgentServiceClient;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentDiagnosisFacadeServiceImplTest {

    @Test
    void shouldDelegateDiagnosisToAgentServiceClient() {
        AgentServiceClient client = request -> {
            AgentDiagnosisResponse response = new AgentDiagnosisResponse();
            response.setCustomerId(request.getCustomerId());
            response.setRecordDate(request.getRecordDate());
            response.setMealType(request.getMealType());
            response.setSummary("AI 判断命中客户排除日期");
            return response;
        };
        AgentDiagnosisFacadeServiceImpl service = new AgentDiagnosisFacadeServiceImpl(client);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        AgentDiagnosisResponse response = service.diagnoseMealPlan(request);

        assertEquals(1001L, response.getCustomerId());
        assertEquals("2026-05-17", response.getRecordDate());
        assertEquals("LUNCH", response.getMealType());
        assertEquals("AI 判断命中客户排除日期", response.getSummary());
    }
}
