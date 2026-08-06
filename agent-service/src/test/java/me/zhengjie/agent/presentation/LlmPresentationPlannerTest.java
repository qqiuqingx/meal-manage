package me.zhengjie.agent.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.infrastructure.llm.AgentModelGateway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 展示 planner 只发送无值结构、禁止工具输出并严格解析候选 JSON。 */
class LlmPresentationPlannerTest {
    @Test
    void shouldPlanWithoutBusinessValuesOrToolCallbacks() throws Exception {
        PlannerFixture fixture = client("{\"view\":\"BAR\",\"title\":\"分组数量\",\"dataPath\":\"items\",\"dimensionField\":\"label\",\"metricFields\":[\"value\"],\"dimensionLabel\":\"分组\",\"metricLabels\":[\"数量\"]}");
        FakeGateway gateway = new FakeGateway(fixture.client());
        LlmPresentationPlanner planner = new LlmPresentationPlanner(gateway, new ObjectMapper());
        CardSchemaInspector.SchemaSummary schema = new CardSchemaInspector().inspect(new ObjectMapper().readTree(
            "{\"items\":[{\"customerName\":\"张三\",\"label\":\"A\",\"value\":2}]}"));

        PresentationSuggestion result = planner.plan("UNKNOWN_CARD", schema);

        assertEquals("BAR", result.view());
        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fixture.request()).user(prompt.capture());
        assertTrue(prompt.getValue().contains("customerName"));
        assertTrue(!prompt.getValue().contains("张三"));
        assertTrue(!prompt.getValue().contains("\"value\":2"));
    }

    @Test
    void shouldRejectUnknownOutputFieldsAndMarkdown() throws Exception {
        PlannerFixture fixture = client("{\"view\":\"TABLE\",\"title\":\"表格\",\"dataPath\":\"items\",\"extra\":\"no\"}");
        LlmPresentationPlanner planner = new LlmPresentationPlanner(new FakeGateway(fixture.client()), new ObjectMapper());
        CardSchemaInspector.SchemaSummary schema = new CardSchemaInspector().inspect(
            new ObjectMapper().readTree("{\"items\":[{\"label\":\"A\"}]}"));

        assertThrows(IllegalStateException.class, () -> planner.plan("UNKNOWN_CARD", schema));
    }

    private PlannerFixture client(String text) {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        ChatClientResponse response = mock(ChatClientResponse.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);
        when(client.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenAnswer(invocation -> request);
        when(request.call()).thenReturn(call);
        when(call.chatClientResponse()).thenReturn(response);
        when(response.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(text);
        return new PlannerFixture(client, request);
    }

    private record PlannerFixture(ChatClient client, ChatClient.ChatClientRequestSpec request) { }

    private static final class FakeGateway implements AgentModelGateway {
        private final ChatClient client;

        private FakeGateway(ChatClient client) {
            this.client = client;
        }

        @Override public boolean isConfigured(String profile) { return LlmPresentationPlanner.PROFILE.equals(profile); }
        @Override public ModelProfile profile(String profile) {
            return new ModelProfile(profile, "test", 1000, true, false, 0);
        }
        @Override public ChatClient chatClient(String profile) { return client; }
        @Override public <T> T execute(String profile, java.util.function.Function<ChatClient, T> invocation) {
            return invocation.apply(client);
        }
    }
}
