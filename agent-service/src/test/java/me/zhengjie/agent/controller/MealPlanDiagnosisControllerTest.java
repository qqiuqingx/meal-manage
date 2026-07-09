package me.zhengjie.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.chat.MealPlanChatService;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.LlmConnectivityResponse;
import me.zhengjie.agent.service.LlmConnectivityService;
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
        LlmConnectivityService connectivityService = request -> new LlmConnectivityResponse();
        MealPlanChatService chatService = request -> new AgentChatResponse();
        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MealPlanDiagnosisController(diagnosisService, connectivityService, chatService))
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

    @Test
    void shouldReturnLlmConnectivityResult() throws Exception {
        MealPlanDiagnosisService diagnosisService = request -> new DiagnosisResponse();
        LlmConnectivityService connectivityService = request -> {
            LlmConnectivityResponse response = new LlmConnectivityResponse();
            response.setSuccess(true);
            response.setBaseUrl("https://llm.example.com");
            response.setModel("test-model");
            response.setContent("pong");
            response.setCostMs(12L);
            return response;
        };
        MealPlanChatService chatService = request -> new AgentChatResponse();
        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MealPlanDiagnosisController(diagnosisService, connectivityService, chatService))
            .build();

        mockMvc.perform(post("/api/agent/meal-plan/llm/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("prompt", "ping"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.baseUrl").value("https://llm.example.com"))
            .andExpect(jsonPath("$.model").value("test-model"))
            .andExpect(jsonPath("$.content").value("pong"))
            .andExpect(jsonPath("$.costMs").value(12L));
    }

    @Test
    void shouldReturnChatResponse() throws Exception {
        MealPlanDiagnosisService diagnosisService = request -> new DiagnosisResponse();
        LlmConnectivityService connectivityService = request -> new LlmConnectivityResponse();
        MealPlanChatService chatService = request -> {
            AgentChatResponse response = new AgentChatResponse();
            response.setSessionId("session-1");
            response.setStatus(ChatStatus.NEED_MORE_INFO);
            response.setAssistantMessage("请补充餐次：早餐、午餐还是晚餐？");
            response.setQuickReplies(java.util.List.of("早餐", "午餐", "晚餐"));
            return response;
        };
        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MealPlanDiagnosisController(diagnosisService, connectivityService, chatService))
            .build();

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId(null);
        request.setMessage("查客户 C10001 今天");

        mockMvc.perform(post("/api/agent/meal-plan/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", "request-1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value("request-1"))
            .andExpect(jsonPath("$.sessionId").value("session-1"))
            .andExpect(jsonPath("$.status").value("NEED_MORE_INFO"))
            .andExpect(jsonPath("$.assistantMessage").value("请补充餐次：早餐、午餐还是晚餐？"))
            .andExpect(jsonPath("$.quickReplies[0]").value("早餐"));
    }
}
