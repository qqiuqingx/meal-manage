package me.zhengjie.agent.chat;

import me.zhengjie.agent.application.conversation.ChatCommand;
import me.zhengjie.agent.application.conversation.ChatResult;
import me.zhengjie.agent.application.conversation.ConversationCoordinator;
import me.zhengjie.agent.application.conversation.ConversationExecutionContext;
import me.zhengjie.agent.application.conversation.ConversationHandler;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MealPlanChatServiceImplTest {
    @Test
    void shouldDelegateCompatibilityEntryToCoordinator() {
        ConversationHandler handler = new ConversationHandler() {
            public String handlerId() { return "compatibility"; }
            public boolean supports(ChatCommand command, ConversationExecutionContext context) { return true; }
            public ChatResult handle(ChatCommand command, ConversationExecutionContext context) {
                AgentChatResponse response = new AgentChatResponse(); response.setStatus(ChatStatus.ANSWERED); response.setSessionId(context.sessionId());
                return new ChatResult(response);
            }
        };
        AgentChatRequest request = new AgentChatRequest(); request.setSessionId("session-facade"); request.setMessage("查询");
        AgentChatResponse response = new MealPlanChatServiceImpl(new ConversationCoordinator(List.of(handler))).chat(request);
        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("session-facade", response.getSessionId());
    }
}
