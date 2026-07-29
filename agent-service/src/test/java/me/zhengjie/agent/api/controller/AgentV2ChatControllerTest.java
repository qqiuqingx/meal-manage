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
            response.setConversationStage("ANSWERED");
            return response;
        };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(service))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();

        Map<String, Object> envelope = Map.of(
            "contractVersion", "v2",
            "messageRequest", Map.of("sessionId", "session-v2", "clientMessageId", "message-v2", "message", "查询客户订单"),
            "availableTools", java.util.List.of("listCustomerOrders"),
            "sessionVersion", 7
        );

        mockMvc.perform(post("/api/agent/v2/chat").header("X-Request-Id", "request-v2")
                .header("X-Agent-Access-Context", "signed-context")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(envelope)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contractVersion").value("v2"))
            .andExpect(jsonPath("$.requestId").value("request-v2"))
            .andExpect(jsonPath("$.clientMessageId").value("message-v2"))
            .andExpect(jsonPath("$.sessionId").value("session-v2"))
            .andExpect(jsonPath("$.expectedSessionVersion").value(7))
            .andExpect(jsonPath("$.conversationPatch.conversationStage").value("ANSWERED"));
    }

    @Test
    void shouldReturnStableValidationError() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(request -> new AgentChatResponse()))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();

        mockMvc.perform(post("/api/agent/v2/chat").header("X-Request-Id", "invalid-v2")
                .header("X-Agent-Access-Context", "signed-context")
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
                .header("X-Agent-Access-Context", "signed-context")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contractVersion\":\"v3\",\"messageRequest\":{\"sessionId\":\"session-v3\",\"clientMessageId\":\"message-v3\",\"message\":\"查询订单\"},\"availableTools\":[],\"sessionVersion\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CONTRACT_VERSION_MISMATCH"))
            .andExpect(jsonPath("$.requestId").value("unsupported-v3"))
            .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void shouldRejectMissingTrustedMessageEnvelopeAsValidationError() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(request -> new AgentChatResponse()))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();

        mockMvc.perform(post("/api/agent/v2/chat").header("X-Request-Id", "missing-message")
                .header("X-Agent-Access-Context", "signed-context")
                .contentType(MediaType.APPLICATION_JSON).content("{\"contractVersion\":\"v2\",\"availableTools\":[],\"sessionVersion\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.details.messageRequest").exists());
    }

    @Test
    void shouldRejectMissingTrustedAccessContextHeader() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(request -> new AgentChatResponse()))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();

        mockMvc.perform(post("/api/agent/v2/chat").header("X-Request-Id", "missing-access")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contractVersion\":\"v2\",\"messageRequest\":{\"sessionId\":\"session-v2\",\"clientMessageId\":\"message-v2\",\"message\":\"查询订单\"},\"availableTools\":[],\"sessionVersion\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void shouldMapCapabilityAndSessionFailuresToStableCodes() throws Exception {
        String body = "{\"contractVersion\":\"v2\",\"messageRequest\":{\"sessionId\":\"session-v2\","
            + "\"clientMessageId\":\"message-v2\",\"message\":\"查询订单\"},\"availableTools\":[],\"sessionVersion\":0}";

        MockMvc missingCapability = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(request -> {
            throw new IllegalArgumentException("CAPABILITY_NOT_AVAILABLE: TEST");
        })).setControllerAdvice(new AgentApiExceptionHandler()).build();
        missingCapability.perform(post("/api/agent/v2/chat")
                .header("X-Request-Id", "missing-capability")
                .header("X-Agent-Access-Context", "signed-context")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CAPABILITY_NOT_AVAILABLE"))
            .andExpect(jsonPath("$.retryable").value(false));

        MockMvc versionConflict = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(request -> {
            throw new IllegalStateException("SESSION_VERSION_CONFLICT");
        })).setControllerAdvice(new AgentApiExceptionHandler()).build();
        versionConflict.perform(post("/api/agent/v2/chat")
                .header("X-Request-Id", "session-conflict")
                .header("X-Agent-Access-Context", "signed-context")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SESSION_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.retryable").value(true));
    }
}
