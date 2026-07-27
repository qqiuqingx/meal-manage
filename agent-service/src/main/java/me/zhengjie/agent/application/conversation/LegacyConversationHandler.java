package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.chat.MealPlanChatServiceImpl;
import org.springframework.stereotype.Component;

/** 迁移期适配器：将未拆分的历史聊天实现纳入 Handler 路由。 */
@Component
public class LegacyConversationHandler implements ConversationHandler {
    private final MealPlanChatServiceImpl legacyChatService;

    public LegacyConversationHandler(MealPlanChatServiceImpl legacyChatService) {
        this.legacyChatService = legacyChatService;
    }

    public String handlerId() { return "legacy-conversation"; }
    public boolean supports(ChatCommand command, ConversationExecutionContext context) { return true; }

    /** 委托历史实现，保证切换前后响应结构保持完全一致。 */
    public ChatResult handle(ChatCommand command, ConversationExecutionContext context) {
        return new ChatResult(legacyChatService.chat(command.request()));
    }
}
