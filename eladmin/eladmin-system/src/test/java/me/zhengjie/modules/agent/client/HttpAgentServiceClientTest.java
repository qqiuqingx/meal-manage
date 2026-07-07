package me.zhengjie.modules.agent.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpAgentServiceClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldPostChatRequestToAgentService() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/agent/meal-plan/chat", this::handleChat);
        server.createContext("/api/agent/meal-plan/diagnose", this::handleDiagnose);
        server.start();

        HttpAgentServiceClient client = new HttpAgentServiceClient();
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(client, "chatPath", "/api/agent/meal-plan/chat");
        ReflectionTestUtils.setField(client, "connectTimeout", 1000);
        ReflectionTestUtils.setField(client, "readTimeout", 1000);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId(null);
        request.setMessage("查 C10001 今天午餐");

        AgentChatResponse response = client.chatMealPlan(request, "request-1");

        assertEquals("session-1", response.getSessionId());
        assertEquals("ANSWERED", response.getStatus());
        assertEquals("已完成诊断", response.getAssistantMessage());
        assertEquals("C10001", response.getSlots().getCustomerCode());
        assertEquals("request-1", response.getRequestId());
    }

    @Test
    void shouldPostDiagnosisRequestAndKeepNewFields() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/agent/meal-plan/diagnose", this::handleDiagnose);
        server.start();

        HttpAgentServiceClient client = new HttpAgentServiceClient();
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(client, "diagnosePath", "/api/agent/meal-plan/diagnose");
        ReflectionTestUtils.setField(client, "connectTimeout", 1000);
        ReflectionTestUtils.setField(client, "readTimeout", 1000);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerCode("C10001");
        request.setRecordDate("2026-05-22");
        request.setMealType("LUNCH");

        AgentDiagnosisResponse response = client.diagnoseMealPlan(request);

        assertEquals("HIGH", response.getConfidence());
        assertEquals("请先核对客户停送登记。", response.getReasons().get(0).getSuggestion());
        assertEquals("CUSTOMER_EXCLUDE_DATE_HIT", response.getReasons().get(0).getRuleIds().get(0));
        assertEquals("核对客户档案停送配置", response.getNextActions().get(0));
        assertEquals("TOOL_CALL", response.getToolCallSummary().get(0).get("eventType"));
    }

    @Test
    void shouldReturnChatFallbackWhenAgentServiceUnavailable() {
        HttpAgentServiceClient client = new HttpAgentServiceClient();
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:1");
        ReflectionTestUtils.setField(client, "chatPath", "/api/agent/meal-plan/chat");
        ReflectionTestUtils.setField(client, "connectTimeout", 100);
        ReflectionTestUtils.setField(client, "readTimeout", 100);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId("session-1");
        request.setMessage("查 C10001 今天午餐");

        AgentChatResponse response = client.chatMealPlan(request, "request-1");

        assertEquals("request-1", response.getRequestId());
        assertEquals("session-1", response.getSessionId());
        assertEquals("ERROR", response.getStatus());
        assertEquals("智能排查服务暂不可用，请先按客户、订单、排餐记录和菜单配置人工核对。", response.getAssistantMessage());
        assertNotNull(response.getQuickReplies());
    }

    @Test
    void shouldReturnDiagnosisFallbackWhenAgentServiceUnavailable() {
        HttpAgentServiceClient client = new HttpAgentServiceClient();
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:1");
        ReflectionTestUtils.setField(client, "diagnosePath", "/api/agent/meal-plan/diagnose");
        ReflectionTestUtils.setField(client, "connectTimeout", 100);
        ReflectionTestUtils.setField(client, "readTimeout", 100);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerCode("C10001");
        request.setRecordDate("2026-05-22");
        request.setMealType("LUNCH");

        AgentDiagnosisResponse response = client.diagnoseMealPlan(request);

        assertEquals(true, response.isFallback());
        assertEquals("agent-service 不可用或调用失败，需人工核对。", response.getFallbackReason());
        assertEquals("LOW", response.getConfidence());
        assertEquals("核对客户档案", response.getNextActions().get(0));
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
        String response = "{\"sessionId\":\"session-1\",\"status\":\"ANSWERED\",\"assistantMessage\":\"已完成诊断\",\"slots\":{\"customerCode\":\"C10001\",\"recordDate\":\"2026-05-22\",\"mealType\":\"LUNCH\"},\"diagnosisResult\":{\"requestId\":\"" + requestId + "\",\"summary\":\"命中规则\",\"fallback\":false,\"reasons\":[]},\"quickReplies\":[\"继续追问\"]}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void handleDiagnose(HttpExchange exchange) throws IOException {
        String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
        String response = """
            {"requestId":"%s","summary":"命中规则","confidence":"HIGH","fallback":false,"nextActions":["核对客户档案停送配置"],"toolCallSummary":[{"eventType":"TOOL_CALL","toolName":"getCustomerProfile"}],"reasons":[{"code":"CUSTOMER_EXCLUDE_DATE_HIT","title":"命中客户停送日期","level":"HIGH","confidence":"HIGH","ruleIds":["CUSTOMER_EXCLUDE_DATE_HIT"],"description":"客户档案中配置了该日期午餐停送。","suggestion":"请先核对客户停送登记。","nextActions":["核对客户档案停送配置"],"evidence":[{"label":"ruleId","value":"CUSTOMER_EXCLUDE_DATE_HIT"}]}]}
            """.formatted(requestId);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
