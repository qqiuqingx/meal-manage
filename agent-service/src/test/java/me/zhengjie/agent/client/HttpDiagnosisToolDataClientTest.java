package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpDiagnosisToolDataClientTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    private MockRestServiceServer server;
    private HttpDiagnosisToolDataClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpDiagnosisToolDataClient(builder, "http://localhost:8000", INTERNAL_TOKEN);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldRejectBlankInternalToken() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new HttpDiagnosisToolDataClient(RestClient.builder(), "http://localhost:8000", "   ")
        );

        assertEquals("agent.internal-token must be configured", exception.getMessage());
    }

    @Test
    void shouldFetchCustomerProfile() {
        DiagnosisToolCustomerLookupRequest request = new DiagnosisToolCustomerLookupRequest();
        request.setCustomerId(1001L);
        request.setCustomerCode("C1001");
        MDC.put("requestId", "trace-1001");

        server.expect(requestTo("http://localhost:8000/api/internal/agent/customer-profile"))
            .andExpect(method(POST))
            .andExpect(header("X-Request-Id", "trace-1001"))
            .andExpect(header("X-Agent-Internal-Token", INTERNAL_TOKEN))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"customerId\":1001,\"customerCode\":\"C1001\"}", false))
            .andRespond(withSuccess("{\"id\":1001,\"customerName\":\"张三\"}", APPLICATION_JSON));

        Map<String, Object> result = client.getCustomerProfile(request);

        assertNotNull(result);
        assertEquals(1001, result.get("id"));
        assertEquals("张三", result.get("customerName"));
        server.verify();
    }

    @Test
    void shouldListCustomerOrders() {
        DiagnosisToolCustomerOrdersRequest request = new DiagnosisToolCustomerOrdersRequest();
        request.setCustomerId(1001L);
        request.setCustomerCode("C1001");
        request.setPage(1);
        request.setSize(20);

        server.expect(requestTo("http://localhost:8000/api/internal/agent/customer-orders"))
            .andExpect(method(POST))
            .andExpect(header("X-Request-Id", ""))
            .andExpect(header("X-Agent-Internal-Token", INTERNAL_TOKEN))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"customerId\":1001,\"customerCode\":\"C1001\",\"page\":1,\"size\":20}", false))
            .andRespond(withSuccess("[{\"id\":2001,\"customerId\":1001}]", APPLICATION_JSON));

        List<Map<String, Object>> result = client.listCustomerOrders(request);

        assertEquals(1, result.size());
        assertEquals(2001, result.get(0).get("id"));
        server.verify();
    }

    @Test
    void shouldFetchMealPlan() {
        DiagnosisToolMealPlanLookupRequest request = new DiagnosisToolMealPlanLookupRequest();
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        server.expect(requestTo("http://localhost:8000/api/internal/agent/meal-plan"))
            .andExpect(method(POST))
            .andExpect(header("X-Request-Id", ""))
            .andExpect(header("X-Agent-Internal-Token", INTERNAL_TOKEN))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"recordDate\":\"2026-05-17\",\"mealType\":\"LUNCH\"}", false))
            .andRespond(withSuccess("{\"totalCustomers\":3}", APPLICATION_JSON));

        Map<String, Object> result = client.getMealPlan(request);

        assertEquals(3, result.get("totalCustomers"));
        server.verify();
    }

    @Test
    void shouldFetchCandidateDishStats() {
        DiagnosisToolCandidateDishStatsRequest request = new DiagnosisToolCandidateDishStatsRequest();
        request.setRecordDate("2026-05-17");

        server.expect(requestTo("http://localhost:8000/api/internal/agent/candidate-dish-stats"))
            .andExpect(method(POST))
            .andExpect(header("X-Request-Id", ""))
            .andExpect(header("X-Agent-Internal-Token", INTERNAL_TOKEN))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"recordDate\":\"2026-05-17\"}", false))
            .andRespond(withSuccess("[{\"packageCode\":\"PKG001\"}]", APPLICATION_JSON));

        List<Map<String, Object>> result = client.getCandidateDishStats(request);

        assertEquals(1, result.size());
        assertEquals("PKG001", result.get(0).get("packageCode"));
        server.verify();
    }
}
