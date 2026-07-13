package me.zhengjie.modules.agent.service.impl;

import me.zhengjie.modules.agent.client.AgentServiceClient;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsDto;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsQuery;
import me.zhengjie.modules.agent.service.AgentOperationStatsService;
import me.zhengjie.modules.agent.security.AgentAccessContext;
import me.zhengjie.modules.agent.security.AgentAccessContextService;
import me.zhengjie.modules.agent.security.AgentQueryPermissionService;
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
                response.setConfidence("HIGH");
                return response;
            }

            @Override
            public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
                return new AgentChatResponse();
            }
        };
        AgentDiagnosisFacadeServiceImpl service = service(client);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        AgentDiagnosisResponse response = service.diagnoseMealPlan(request);

        assertEquals(1001L, response.getCustomerId());
        assertEquals("2026-05-17", response.getRecordDate());
        assertEquals("LUNCH", response.getMealType());
        assertEquals("AI 判断命中客户排除日期", response.getSummary());
        assertEquals("HIGH", response.getConfidence());
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
                response.setConversationStage("COLLECTING_SLOTS");
                return response;
            }
        };
        AgentDiagnosisFacadeServiceImpl service = service(client);

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("查客户 C10001 今天");

        AgentChatResponse response = service.chatMealPlan(request, "request-1");

        assertEquals("session-1", response.getSessionId());
        assertEquals("NEED_MORE_INFO", response.getStatus());
        assertEquals("request-1", response.getRequestId());
        assertEquals("请补充餐次：早餐、午餐还是晚餐？", response.getAssistantMessage());
        assertEquals("COLLECTING_SLOTS", response.getConversationStage());
    }

    private AgentOperationStatsService noopStatsService() {
        return new AgentOperationStatsService() {
            @Override
            public void recordDiagnosis(AgentDiagnosisResponse response, String sessionId, long costMs) {
            }

            @Override
            public AgentOperationStatsDto stats(AgentOperationStatsQuery query) {
                return new AgentOperationStatsDto();
            }
        };
    }

    /** 创建只用于客户端委派测试的 Facade，避免依赖登录态。 */
    private AgentDiagnosisFacadeServiceImpl service(AgentServiceClient client) {
        AgentAccessContextService contextService = new AgentAccessContextService() {
            @Override public String issue(String sessionId, String requestId) { return "token"; }
            @Override public AgentAccessContext verify(String token, String sessionId, String requestId) { return new AgentAccessContext(); }
        };
        AgentQueryPermissionService permissionService = new AgentQueryPermissionService() {
            @Override public void require(AgentAccessContext context, String... requiredPermissions) { }
            @Override public java.util.List<String> availableToolNames(AgentAccessContext context) { return java.util.List.of(); }
        };
        return new AgentDiagnosisFacadeServiceImpl(client, noopStatsService(), contextService, permissionService);
    }
}
