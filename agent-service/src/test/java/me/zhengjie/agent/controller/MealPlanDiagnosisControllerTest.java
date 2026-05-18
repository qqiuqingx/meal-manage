package me.zhengjie.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MealPlanDiagnosisControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnDiagnosisResponse() throws Exception {
        MealPlanDiagnosisService diagnosisService = request -> {
            DiagnosisResponse response = new DiagnosisResponse();
            response.setCustomerId(1001L);
            response.setCustomerName("张三");
            response.setSummary("命中客户排除日期");
            response.setReasons(Collections.emptyList());
            return response;
        };
        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MealPlanDiagnosisController(diagnosisService))
            .build();

        DiagnosisResponse response = new DiagnosisResponse();
        response.setCustomerId(1001L);
        response.setCustomerName("张三");
        response.setSummary("命中客户排除日期");
        response.setReasons(Collections.emptyList());

        DiagnosisRequest request = new DiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        mockMvc.perform(post("/api/agent/meal-plan/diagnose")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerId").value(1001L))
            .andExpect(jsonPath("$.summary").value("命中客户排除日期"));
    }
}
