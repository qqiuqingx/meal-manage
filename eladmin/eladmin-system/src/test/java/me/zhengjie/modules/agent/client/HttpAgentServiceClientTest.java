package me.zhengjie.modules.agent.client;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        AgentChatResponse response = client.chatMealPlan(request, "request-1");

        assertEquals("session-1", response.getSessionId());
        assertEquals("ANSWERED", response.getStatus());
        assertEquals("已完成诊断", response.getAssistantMessage());
        assertEquals("C10001", response.getSlots().getCustomerCode());
        assertEquals("request-1", response.getRequestId());
    }

    @Test
    void shouldPostDiagnosisRequestAndKeepNewFields() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String responseBody = """
            {"requestId":"request-1","summary":"命中规则","confidence":"HIGH","fallback":false,"nextActions":["核对客户档案停送配置"],"toolCallSummary":[{"eventType":"TOOL_CALL","toolName":"getCustomerProfile"}],"reasons":[{"code":"CUSTOMER_EXCLUDE_DATE_HIT","title":"命中客户停送日期","level":"HIGH","confidence":"HIGH","ruleIds":["CUSTOMER_EXCLUDE_DATE_HIT"],"description":"客户档案中配置了该日期午餐停送。","suggestion":"请先核对客户停送登记。","nextActions":["核对客户档案停送配置"],"evidence":[{"label":"ruleId","value":"CUSTOMER_EXCLUDE_DATE_HIT"}]}]}
            """;
        when(restTemplate.postForEntity(anyString(), any(), any())).thenReturn(ResponseEntity.ok(responseBody));

        HttpAgentServiceClient client = clientWithRestTemplate(restTemplate);

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
        RestTemplate restTemplate = mock(RestTemplate.class);
        doThrow(new ResourceAccessException("connection refused")).when(restTemplate).postForEntity(anyString(), any(), any());

        HttpAgentServiceClient client = clientWithRestTemplate(restTemplate);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId("session-1");
        request.setMessage("查 C10001 今天午餐");

        AgentChatResponse response = client.chatMealPlan(request, "request-1");

        assertEquals("request-1", response.getRequestId());
        assertEquals("session-1", response.getSessionId());
        assertEquals("ERROR", response.getStatus());
        assertEquals("智能排查服务不可用，已生成兜底人工复核建议。", response.getAssistantMessage());
        assertEquals("AGENT_SERVICE_UNAVAILABLE", response.getDiagnosisResult().getFailureType());
        assertEquals("ELADMIN_CLIENT", response.getDiagnosisResult().getFallbackSource());
        assertNotNull(response.getQuickReplies());
    }

    @Test
    void shouldReturnDiagnosisFallbackWhenAgentServiceUnavailable() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        doThrow(new ResourceAccessException("connection refused")).when(restTemplate).postForEntity(anyString(), any(), any());

        HttpAgentServiceClient client = clientWithRestTemplate(restTemplate);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerCode("C10001");
        request.setRecordDate("2026-05-22");
        request.setMealType("LUNCH");

        AgentDiagnosisResponse response = client.diagnoseMealPlan(request);

        assertEquals(true, response.isFallback());
        assertEquals("智能排查服务不可用，已生成兜底人工复核建议。", response.getFallbackReason());
        assertEquals("ELADMIN_CLIENT", response.getFallbackSource());
        assertEquals("AGENT_SERVICE_UNAVAILABLE", response.getFailureType());
        assertEquals("LOW", response.getConfidence());
        assertEquals("核对客户档案", response.getNextActions().get(0));
    }

    @Test
    void shouldRetryOnServerErrorThenSucceed() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String responseBody = """
            {"requestId":"request-2","summary":"命中规则","confidence":"HIGH","fallback":false,"nextActions":["核对客户档案停送配置"],"toolCallSummary":[],"reasons":[]}
            """;
        when(restTemplate.postForEntity(anyString(), any(), any()))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .thenReturn(ResponseEntity.ok(responseBody));

        HttpAgentServiceClient client = clientWithRestTemplate(restTemplate);
        ReflectionTestUtils.setField(client, "retryTimes", 1);
        ReflectionTestUtils.setField(client, "retryBackoffMs", 1L);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerCode("C10001");
        request.setRecordDate("2026-05-22");
        request.setMealType("LUNCH");

        AgentDiagnosisResponse response = client.diagnoseMealPlan(request);

        verify(restTemplate, times(2)).postForEntity(anyString(), any(), any());
        assertEquals(false, response.isFallback());
        assertEquals("HIGH", response.getConfidence());
    }

    @Test
    void shouldFallbackOnBadResponseWithoutRetry() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(anyString(), any(), any())).thenReturn(ResponseEntity.ok("{bad json"));

        HttpAgentServiceClient client = clientWithRestTemplate(restTemplate);
        ReflectionTestUtils.setField(client, "retryTimes", 1);
        ReflectionTestUtils.setField(client, "retryBackoffMs", 1L);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerCode("C10001");
        request.setRecordDate("2026-05-22");
        request.setMealType("LUNCH");

        AgentDiagnosisResponse response = client.diagnoseMealPlan(request);

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), any());
        assertEquals(true, response.isFallback());
        assertEquals("AGENT_SERVICE_BAD_RESPONSE", response.getFailureType());
        assertEquals("ELADMIN_CLIENT", response.getFallbackSource());
        assertEquals("智能排查服务返回异常，已生成兜底人工复核建议。", response.getFallbackReason());
    }

    @Test
    void shouldRetryOnTimeout() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(anyString(), any(), any()))
            .thenThrow(new ResourceAccessException("timed out", new SocketTimeoutException("timed out")))
            .thenThrow(new ResourceAccessException("timed out", new SocketTimeoutException("timed out")));

        HttpAgentServiceClient client = clientWithRestTemplate(restTemplate);
        ReflectionTestUtils.setField(client, "retryTimes", 1);
        ReflectionTestUtils.setField(client, "retryBackoffMs", 1L);

        AgentDiagnosisRequest request = new AgentDiagnosisRequest();
        request.setCustomerCode("C10001");
        request.setRecordDate("2026-05-22");
        request.setMealType("LUNCH");

        AgentDiagnosisResponse response = client.diagnoseMealPlan(request);

        verify(restTemplate, times(2)).postForEntity(anyString(), any(), any());
        assertEquals("AGENT_SERVICE_TIMEOUT", response.getFailureType());
        assertEquals(true, response.isFallback());
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
