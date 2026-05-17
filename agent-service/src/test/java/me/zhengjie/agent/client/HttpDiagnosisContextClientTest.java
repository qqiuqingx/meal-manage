package me.zhengjie.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class HttpDiagnosisContextClientTest {

    @Test
    void shouldFetchAndConvertRemoteContext() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new ObjectMapper();

        String responseJson = """
            {
              "customerId": 1001,
              "customerCode": "C1001",
              "customerName": "张三",
              "recordDate": "2026-05-17",
              "mealType": "LUNCH",
              "customerProfile": {
                "excludeDates": [
                  {"date": "2026-05-17", "mealTypes": ["LUNCH"]}
                ]
              },
              "orders": [
                {"orderCode": "ORD20260517001", "status": 1, "startDate": "2026-05-01", "mealType": "LUNCH"}
              ],
              "mealPlan": {"id": 3001, "status": "SUCCESS"},
              "customerPlans": []
            }
            """;

        server.expect(requestTo("http://localhost:18080/api/internal/agent/meal-plan/context"))
            .andExpect(method(POST))
            .andExpect(content().contentType(APPLICATION_JSON))
            .andRespond(withSuccess(responseJson, APPLICATION_JSON));

        HttpDiagnosisContextClient client = new HttpDiagnosisContextClient(
            builder,
            objectMapper,
            "http://localhost:18080",
            "/api/internal/agent/meal-plan/context"
        );

        DiagnosisRequest request = new DiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        DiagnosisContextDto context = client.fetch(request);

        assertNotNull(context);
        assertEquals(1001L, context.getCustomerId());
        assertEquals("C1001", context.getCustomerCode());
        assertEquals("张三", context.getCustomerName());
        assertEquals(1, context.getOrders().size());
        assertEquals("SUCCESS", String.valueOf(context.getMealPlan().get("status")));

        server.verify();
    }
}
