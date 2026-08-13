package me.zhengjie.agent.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.api.error.AgentApiExceptionHandler;
import me.zhengjie.agent.application.BusinessAgentRunner;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
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
    void shouldReturnContractVersionAndClientMessageId() throws Exception {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-v2");
        response.setStatus(ChatStatus.ANSWERED);
        response.setAssistantMessage("已完成查询");
        response.setConversationStage("ANSWERED");
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode("B3303");
        response.setSlots(slots);
        response.setLastBusinessQueryContext(Map.of("lastToolName", "listMealPlans"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(runnerReturning(response)))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();

        Map<String, Object> envelope = Map.of(
            "contractVersion", "v2",
            "messageRequest", Map.of("sessionId", "session-v2", "clientMessageId", "message-v2", "message", "查询客户订单"),
            "availableTools", java.util.List.of("searchServiceCustomers"),
            "formDraftContext", Map.of("draftId", "afd_1234567890abcdef", "revision", 2),
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
            .andExpect(jsonPath("$.conversationPatch.conversationStage").value("ANSWERED"))
            .andExpect(jsonPath("$.conversationPatch.slots.customerCode").value("B3303"))
            .andExpect(jsonPath("$.conversationPatch.lastBusinessQueryContext.lastToolName").value("listMealPlans"));
    }

    @Test
    void shouldReturnStableValidationError() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(runnerReturning(null)))
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
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(runnerReturning(null)))
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
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(runnerReturning(null)))
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
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(runnerReturning(null)))
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

        BusinessAgentRunner missingCapabilityRunner = runnerThrowing(
            new IllegalArgumentException("CAPABILITY_NOT_AVAILABLE: TEST"));
        MockMvc missingCapability = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(missingCapabilityRunner))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();
        missingCapability.perform(post("/api/agent/v2/chat")
                .header("X-Request-Id", "missing-capability")
                .header("X-Agent-Access-Context", "signed-context")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CAPABILITY_NOT_AVAILABLE"))
            .andExpect(jsonPath("$.retryable").value(false));

        BusinessAgentRunner versionConflictRunner = runnerThrowing(new IllegalStateException("SESSION_VERSION_CONFLICT"));
        MockMvc versionConflict = MockMvcBuilders.standaloneSetup(new AgentV2ChatController(versionConflictRunner))
            .setControllerAdvice(new AgentApiExceptionHandler()).build();
        versionConflict.perform(post("/api/agent/v2/chat")
                .header("X-Request-Id", "session-conflict")
                .header("X-Agent-Access-Context", "signed-context")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SESSION_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.retryable").value(true));
    }

    private BusinessAgentRunner runnerReturning(AgentChatResponse response) {
        return new StubBusinessAgentRunner(response, null);
    }

    private BusinessAgentRunner runnerThrowing(RuntimeException failure) {
        return new StubBusinessAgentRunner(null, failure);
    }

    private static final class StubBusinessAgentRunner extends BusinessAgentRunner {
        private final AgentChatResponse response;
        private final RuntimeException failure;

        private StubBusinessAgentRunner(AgentChatResponse response, RuntimeException failure) {
            super(null, null, null, null, null, new ObjectMapper(), null);
            this.response = response;
            this.failure = failure;
        }

        /** 返回测试预设结果或抛出测试预设异常。 */
        @Override
        public AgentChatResponse run(AgentChatRequest request) {
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}
