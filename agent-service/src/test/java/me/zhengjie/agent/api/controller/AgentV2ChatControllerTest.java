package me.zhengjie.agent.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.api.error.AgentApiExceptionHandler;
import me.zhengjie.agent.chat.MealPlanChatService;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentV2ChatControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnContractVersionAndClientMessageIdWithoutChangingLegacyService() throws Exception {
        MealPlanChatService service = request -> {
            AgentChatResponse response = new AgentChatResponse();
            response.setSessionId(request.getSessionId());
            response.setStatus(ChatStatus.ANSWERED);
            response.setAssistantMessage("已完成查询");
            return response;
        };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(service))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();

        Map<String, Object> envelope = Map.of(
            "contractVersion", "v2",
            "messageRequest", Map.of("sessionId", "session-v2", "clientMessageId", "message-v2", "message", "查询客户订单"),
            "availableTools", java.util.List.of("listCustomerOrders")
        );

        mockMvc.perform(post("/api/agent/v2/chat").header("X-Request-Id", "request-v2")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(envelope)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contractVersion").value("v2"))
            .andExpect(jsonPath("$.requestId").value("request-v2"))
            .andExpect(jsonPath("$.clientMessageId").value("message-v2"))
            .andExpect(jsonPath("$.sessionId").value("session-v2"));
    }

    @Test
    void shouldReturnStableValidationError() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(request -> new AgentChatResponse()))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();

        mockMvc.perform(post("/api/agent/v2/chat").header("X-Request-Id", "invalid-v2")
                .contentType(MediaType.APPLICATION_JSON).content("{\"contractVersion\":\"v2\",\"messageRequest\":{\"message\":\"\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.requestId").value("invalid-v2"))
            .andExpect(jsonPath("$.retryable").value(false))
            .andExpect(jsonPath("$.details").isNotEmpty());
    }

    @Test
    void shouldRejectUnsupportedContractVersionWithoutInternalDetails() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(request -> new AgentChatResponse()))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();

        mockMvc.perform(post("/api/agent/v2/chat").header("X-Request-Id", "unsupported-v3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contractVersion\":\"v3\",\"messageRequest\":{\"message\":\"查询订单\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CONTRACT_VERSION_MISMATCH"))
            .andExpect(jsonPath("$.requestId").value("unsupported-v3"))
            .andExpect(jsonPath("$.retryable").value(false));
    }
}
