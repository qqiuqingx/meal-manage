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

import java.util.List;
import java.util.Map;

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
        assertTrue(response.getPresentations().isEmpty());
    }

    @Test
    /** Agent v2 服务客户展示描述和六列业务事实必须原样透传到主系统。 */
    void shouldSendTrustedV2EnvelopeAndParseContractFields() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String responseBody = "{\"contractVersion\":\"v2\",\"clientMessageId\":\"message-v2\",\"sessionId\":\"session-v2\",\"status\":\"ANSWERED\",\"cards\":[{\"type\":\"SERVICE_CUSTOMER_LIST\",\"sourceToolCallId\":\"call-1\",\"data\":{\"total\":2,\"items\":[{\"customerCode\":\"C10001\",\"customerName\":\"张三\",\"orderCode\":\"O-001\",\"orderTime\":\"2026-08-01T09:00:00+08:00\",\"status\":\"ACTIVE\",\"parentPackageName\":\"标准套餐\"},{\"customerCode\":\"C10002\",\"customerName\":\"李四\",\"orderCode\":\"O-002\",\"orderTime\":\"2026-08-02T10:00:00+08:00\",\"status\":\"ACTIVE\",\"parentPackageName\":\"轻食套餐\"}]}}],\"presentations\":[{\"schemaVersion\":\"v1\",\"sourceToolCallId\":\"call-1\",\"cardType\":\"SERVICE_CUSTOMER_LIST\",\"decisionSource\":\"SYSTEM\",\"title\":\"服务客户下单明细\",\"layout\":\"TABS\",\"defaultView\":\"TABLE\",\"availableViews\":[\"TABLE\"],\"table\":{\"dataPath\":\"items\",\"columns\":[{\"field\":\"customerCode\",\"label\":\"客户编号\",\"format\":\"TEXT\"},{\"field\":\"customerName\",\"label\":\"姓名\",\"format\":\"TEXT\"},{\"field\":\"orderCode\",\"label\":\"订单编号\",\"format\":\"TEXT\"},{\"field\":\"orderTime\",\"label\":\"下单时间\",\"format\":\"DATE_TIME\"},{\"field\":\"status\",\"label\":\"订单状态\",\"format\":\"STATUS\"},{\"field\":\"parentPackageName\",\"label\":\"套餐\",\"format\":\"TEXT\"}],\"sections\":[]}}],\"facts\":[{\"label\":\"订单数\",\"value\":\"2\"}],\"lastBusinessQueryContext\":{\"toolName\":\"searchServiceCustomers\"}}";
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
        Map<String, Object> cardData = (Map<String, Object>) response.getCards().get(0).get("data");
        List<Map<String, Object>> items = (List<Map<String, Object>>) cardData.get("items");
        assertEquals(2, items.size());
        assertEquals("C10001", items.get(0).get("customerCode"));
        assertEquals("张三", items.get(0).get("customerName"));
        assertEquals("2026-08-01T09:00:00+08:00", items.get(0).get("orderTime"));
        assertEquals("C10002", items.get(1).get("customerCode"));
        assertEquals("O-002", items.get(1).get("orderCode"));
        assertEquals(1, response.getPresentations().size());
        assertEquals("v1", response.getPresentations().get(0).get("schemaVersion"));
        assertEquals("call-1", response.getPresentations().get(0).get("sourceToolCallId"));
        assertEquals("SERVICE_CUSTOMER_LIST", response.getPresentations().get(0).get("cardType"));
        assertEquals("SYSTEM", response.getPresentations().get(0).get("decisionSource"));
        assertEquals("TABLE", response.getPresentations().get(0).get("defaultView"));
        assertEquals("items", ((Map<String, Object>) response.getPresentations().get(0).get("table")).get("dataPath"));
        List<Map<String, Object>> columns = (List<Map<String, Object>>)
            ((Map<String, Object>) response.getPresentations().get(0).get("table")).get("columns");
        assertEquals(List.of("customerCode", "customerName", "orderCode", "orderTime", "status", "parentPackageName"),
            columns.stream().map(column -> (String) column.get("field")).collect(java.util.stream.Collectors.toList()));
        assertEquals("searchServiceCustomers", response.getLastBusinessQueryContext().get("toolName"));
    }

    @Test
    void shouldTreatNullPresentationsAsEmptyList() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String responseBody = "{\"sessionId\":\"session-null\",\"status\":\"ANSWERED\",\"presentations\":null}";
        when(restTemplate.postForEntity(anyString(), any(), any())).thenReturn(ResponseEntity.ok(responseBody));

        AgentChatResponse response = clientWithRestTemplate(restTemplate)
            .chatMealPlan(new AgentChatRequest(), "request-null", null);

        assertNotNull(response.getPresentations());
        assertTrue(response.getPresentations().isEmpty());
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
