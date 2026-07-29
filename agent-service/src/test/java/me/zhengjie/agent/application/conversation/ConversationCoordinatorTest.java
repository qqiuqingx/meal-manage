package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversationCoordinatorTest {
    @Test
    void shouldRouteNewHandlerWithoutChangingCoordinator() {
        ConversationHandler handler = new ConversationHandler() {
            public String handlerId() { return "test-capability"; }
            public boolean supports(ChatCommand command, ConversationExecutionContext context) { return true; }
            public ChatResult handle(ChatCommand command, ConversationExecutionContext context) {
                AgentChatResponse response = new AgentChatResponse(); response.setStatus(ChatStatus.ANSWERED); return new ChatResult(response);
            }
        };
        AgentChatRequest request = new AgentChatRequest(); request.setMessage("测试");
        ChatResult result = new ConversationCoordinator(List.of(handler))
            .coordinate(new ChatCommand(request), new ConversationExecutionContext("request-1", "session-1"));
        assertEquals(ChatStatus.ANSWERED, result.response().getStatus());
    }

    @Test
    void shouldRejectAmbiguousHandlers() {
        ConversationHandler handler = new ConversationHandler() {
            public String handlerId() { return "same"; }
            public boolean supports(ChatCommand command, ConversationExecutionContext context) { return true; }
            public ChatResult handle(ChatCommand command, ConversationExecutionContext context) { return null; }
        };
        AgentChatRequest request = new AgentChatRequest(); request.setMessage("测试");
        assertThrows(IllegalStateException.class, () -> new ConversationCoordinator(List.of(handler, handler))
            .coordinate(new ChatCommand(request), new ConversationExecutionContext("request-1", "session-1")));
    }

    @Test
    void coordinatorMustNotDependOnHttpClientImplementations() {
        for (Field field : ConversationCoordinator.class.getDeclaredFields()) {
            assertEquals(false, field.getType().getSimpleName().startsWith("Http"));
        }
    }

    @Test
    void shouldExposeHandlerAsCapabilityMdcOnlyDuringExecution() {
        MDC.put("capabilityId", "outer");
        ConversationHandler handler = new ConversationHandler() {
            public String handlerId() { return "test-capability"; }
            public boolean supports(ChatCommand command, ConversationExecutionContext context) {
                return true;
            }
            public ChatResult handle(ChatCommand command, ConversationExecutionContext context) {
                assertEquals("test-capability", MDC.get("capabilityId"));
                return new ChatResult(new AgentChatResponse());
            }
        };
        try {
            AgentChatRequest request = new AgentChatRequest();
            request.setMessage("测试");
            new ConversationCoordinator(List.of(handler)).coordinate(
                new ChatCommand(request),
                new ConversationExecutionContext("request-1", "session-1"));

            assertEquals("outer", MDC.get("capabilityId"));
        } finally {
            MDC.clear();
        }
    }
}
