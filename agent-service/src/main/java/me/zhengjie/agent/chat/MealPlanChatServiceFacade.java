package me.zhengjie.agent.chat;

import me.zhengjie.agent.application.conversation.ChatCommand;
import me.zhengjie.agent.application.conversation.ConversationCoordinator;
import me.zhengjie.agent.application.conversation.ConversationExecutionContext;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import org.springframework.stereotype.Service;

/**
 * 兼容聊天 Facade。控制器和 v2 API 只依赖此入口，具体业务由 Coordinator 的 Handler 路由。
 */
@Service
public class MealPlanChatServiceFacade implements MealPlanChatService {
    private final ConversationCoordinator coordinator;

    public MealPlanChatServiceFacade(ConversationCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * 将历史请求转换为应用层命令并返回兼容响应。
     *
     * @param request 历史聊天请求
     * @return 保持字段兼容的聊天响应
     */
    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        String sessionId = request == null ? null : request.getSessionId();
        return coordinator.coordinate(new ChatCommand(request), new ConversationExecutionContext(null, sessionId)).response();
    }
}
