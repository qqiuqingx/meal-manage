package me.zhengjie.modules.agent.client;

import com.alibaba.fastjson2.JSON;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class HttpAgentServiceClientTest {

    @Test
    void shouldPostChatRequestToAgentService() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String responseBody = "{\"sessionId\":\"session-1\",\"status\":\"ANSWERED\",\"assistantMessage\":\"已完成诊断\",\"slots\":{\"customerCode\":\"C10001\",\"recordDate\":\"2026-05-22\",\"mealType\":\"LUNCH\"},\"diagnosisResult\":{\"requestId\":\"request-1\",\"summary\":\"命中规则\",\"fallback\":false,\"reasons\":[]},\"quickReplies\":[\"继续追问\"]}";
        when(restTemplate.postForEntity(anyString(), any(), any())).thenReturn(ResponseEntity.ok(responseBody));

        HttpAgentServiceClient client = clientWithRestTemplate(restTemplate);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId(null);
        request.setMessage("查 C10001 今天午餐");

        AgentChatResponse response =
            client.chatMealPlan(request, "request-1", "signed-context");

        assertEquals("session-1", response.getSessionId());
        assertEquals("ANSWERED", response.getStatus());
        assertEquals("已完成诊断", response.getAssistantMessage());
        assertEquals("C10001", response.getSlots().getCustomerCode());
        assertEquals("request-1", response.getRequestId());
    }

    @Test
    void shouldSendTrustedV2EnvelopeAndParseContractFields() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String responseBody = "{\"contractVersion\":\"v2\",\"clientMessageId\":\"message-v2\",\"sessionId\":\"session-v2\",\"status\":\"ANSWERED\",\"cards\":[{\"type\":\"SERVICE_CUSTOMER_LIST\",\"sourceToolCallId\":\"call-1\",\"data\":{\"total\":1}}],\"facts\":[{\"label\":\"订单数\",\"value\":\"1\"}],\"lastBusinessQueryContext\":{\"toolName\":\"searchServiceCustomers\"}}";
        when(restTemplate.postForEntity(anyString(), any(), any())).thenReturn(ResponseEntity.ok(responseBody));
        HttpAgentServiceClient client = clientWithRestTemplate(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:18081");
        ReflectionTestUtils.setField(client, "chatPath", "/api/agent/v2/chat");

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId("session-v2");
        request.setClientMessageId("message-v2");
        request.setMessage("查询客户订单");
        request.setAvailableTools(java.util.List.of("searchServiceCustomers"));
        request.setSessionVersion(0L);
        AgentChatResponse response = client.chatMealPlan(request, "request-v2", "signed-context");

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(url.capture(), entity.capture(), any());
        String body = String.valueOf(entity.getValue().getBody());
        assertEquals("http://localhost:18081/api/agent/v2/chat", url.getValue());
        assertEquals("v2", JSON.parseObject(body).getString("contractVersion"));
        assertEquals("查询客户订单", JSON.parseObject(body).getJSONObject("messageRequest").getString("message"));
        assertEquals("signed-context", entity.getValue().getHeaders().getFirst("X-Agent-Access-Context"));
        assertEquals("message-v2", response.getClientMessageId());
        assertEquals("v2", response.getContractVersion());
        assertEquals("订单数", response.getFacts().get(0).get("label"));
        assertEquals("SERVICE_CUSTOMER_LIST", response.getCards().get(0).get("type"));
        assertEquals("searchServiceCustomers", response.getLastBusinessQueryContext().get("toolName"));
    }

    @Test
    void shouldReturnChatFallbackWhenAgentServiceUnavailable() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        doThrow(new ResourceAccessException("connection refused")).when(restTemplate).postForEntity(anyString(), any(), any());

        HttpAgentServiceClient client = clientWithRestTemplate(restTemplate);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId("session-1");
        request.setMessage("查 C10001 今天午餐");

        AgentChatResponse response =
            client.chatMealPlan(request, "request-1", "signed-context");

        assertEquals("request-1", response.getRequestId());
        assertEquals("session-1", response.getSessionId());
        assertEquals("ERROR", response.getStatus());
        assertEquals("智能排查服务不可用，已生成兜底人工复核建议。", response.getAssistantMessage());
        assertEquals("AGENT_SERVICE_UNAVAILABLE", response.getWarnings().get(0));
        assertTrue(response.isPartial());
        assertNotNull(response.getQuickReplies());
    }

    private HttpAgentServiceClient clientWithRestTemplate(RestTemplate restTemplate) {
        return new HttpAgentServiceClient() {
            @Override
            protected RestTemplate restTemplate() {
                return restTemplate;
            }
        };
    }
}
