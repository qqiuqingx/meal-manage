package me.zhengjie.agent.observability;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ChatModelCallAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisToolCallLoggingAdvisorTest {

    @Test
    void shouldLogModelRoundWithToolCallNames() {
        RecordingLogger logger = new RecordingLogger();
        DiagnosisToolCallLoggingAdvisor advisor = new DiagnosisToolCallLoggingAdvisor(logger);

        ChatClientResponse response = advisor.adviseCall(request(), chain(responseWithToolCalls("getMealPlan", "listCustomerOrders")));

        assertTrue(response.chatResponse().hasToolCalls());
        assertEquals(2, logger.events().size());
        assertEquals("start:1", logger.events().get(0));
        assertEquals("completed:1:true:2:getMealPlan,listCustomerOrders", logger.events().get(1));
    }

    @Test
    void shouldKeepRoundCounterAcrossRepeatedCallsInSameDiagnosis() {
        RecordingLogger logger = new RecordingLogger();
        DiagnosisToolCallLoggingAdvisor advisor = new DiagnosisToolCallLoggingAdvisor(logger);
        ChatClientRequest request = request();

        advisor.adviseCall(request, chain(responseWithToolCalls("getMealPlan")));
        advisor.adviseCall(request, chain(responseWithoutToolCalls()));

        assertEquals("start:1", logger.events().get(0));
        assertEquals("completed:1:true:1:getMealPlan", logger.events().get(1));
        assertEquals("start:2", logger.events().get(2));
        assertEquals("completed:2:false:0:", logger.events().get(3));
    }

    @Test
    void shouldLogFailureAndRethrow() {
        RecordingLogger logger = new RecordingLogger();
        DiagnosisToolCallLoggingAdvisor advisor = new DiagnosisToolCallLoggingAdvisor(logger);

        assertThrows(IllegalStateException.class, () -> advisor.adviseCall(request(), failingChain()));

        assertEquals(List.of("start:1", "failed:1:IllegalStateException:broken"), logger.events());
    }

    @Test
    void shouldRunAfterToolCallAdvisorAndBeforeChatModelCallAdvisor() {
        DiagnosisToolCallLoggingAdvisor advisor = new DiagnosisToolCallLoggingAdvisor(new RecordingLogger());

        assertTrue(advisor.getOrder() > ToolCallAdvisor.builder().build().getOrder());
        assertTrue(advisor.getOrder() < ChatModelCallAdvisor.builder().chatModel(prompt -> null).build().getOrder());
    }

    private ChatClientRequest request() {
        return ChatClientRequest.builder()
            .prompt(new Prompt("diagnose"))
            .build();
    }

    private CallAdvisorChain chain(ChatClientResponse response) {
        return new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest chatClientRequest) {
                return response;
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }

            @Override
            public CallAdvisorChain copy(CallAdvisor callAdvisor) {
                return this;
            }
        };
    }

    private CallAdvisorChain failingChain() {
        return new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest chatClientRequest) {
                throw new IllegalStateException("broken");
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }

            @Override
            public CallAdvisorChain copy(CallAdvisor callAdvisor) {
                return this;
            }
        };
    }

    private ChatClientResponse responseWithToolCalls(String... toolNames) {
        List<AssistantMessage.ToolCall> toolCalls = java.util.Arrays.stream(toolNames)
            .map(toolName -> new AssistantMessage.ToolCall("call-" + toolName, "function", toolName, "{}"))
            .toList();
        AssistantMessage message = AssistantMessage.builder()
            .content("")
            .toolCalls(toolCalls)
            .build();
        return response(message);
    }

    private ChatClientResponse responseWithoutToolCalls() {
        return response(new AssistantMessage("最终诊断结果"));
    }

    private ChatClientResponse response(AssistantMessage message) {
        return ChatClientResponse.builder()
            .chatResponse(new ChatResponse(List.of(new Generation(message))))
            .build();
    }

    private static class RecordingLogger implements DiagnosisToolCallLoggingAdvisor.LogSink {
        private final List<String> events = new java.util.ArrayList<>();

        @Override
        public void modelCallStart(int round) {
            events.add("start:" + round);
        }

        @Override
        public void modelCallCompleted(int round, boolean hasToolCalls, int toolCallCount, String toolNames, long costMs) {
            events.add("completed:" + round + ":" + hasToolCalls + ":" + toolCallCount + ":" + toolNames);
        }

        @Override
        public void modelCallFailed(int round, RuntimeException ex, long costMs) {
            events.add("failed:" + round + ":" + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }

        private List<String> events() {
            return events;
        }
    }
}
