package me.zhengjie.modules.agent.service.impl;

import me.zhengjie.modules.agent.client.AgentServiceClient;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentDiagnosisFacadeServiceImplTest {

    @Test
    void shouldDelegateDiagnosisToAgentServiceClient() {
        AgentServiceClient client = new AgentServiceClient() {
            @Override
            public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
                AgentDiagnosisResponse response = new AgentDiagnosisResponse();
                response.setCustomerId(request.getCustomerId());
                response.setRecordDate(request.getRecordDate());
                response.setMealType(request.getMealType());
                response.setSummary("AI 判断命中客户排除日期");
                return response;
            }

            @Override
            public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
                return new AgentChatResponse();
            }
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

    @Test
    void shouldDelegateChatToAgentServiceClient() {
        AgentServiceClient client = new AgentServiceClient() {
            @Override
            public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
                return new AgentDiagnosisResponse();
            }

            @Override
            public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
                AgentChatResponse response = new AgentChatResponse();
                response.setSessionId("session-1");
                response.setStatus("NEED_MORE_INFO");
                response.setAssistantMessage("请补充餐次：早餐、午餐还是晚餐？");
                response.setRequestId(requestId);
                return response;
            }
        };
        AgentDiagnosisFacadeServiceImpl service = new AgentDiagnosisFacadeServiceImpl(client);

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("查客户 C10001 今天");

        AgentChatResponse response = service.chatMealPlan(request, "request-1");

        assertEquals("session-1", response.getSessionId());
        assertEquals("NEED_MORE_INFO", response.getStatus());
        assertEquals("request-1", response.getRequestId());
        assertEquals("请补充餐次：早餐、午餐还是晚餐？", response.getAssistantMessage());
    }
}
