package me.zhengjie.agent.chat;

import me.zhengjie.agent.application.conversation.ChatCommand;
import me.zhengjie.agent.application.conversation.ConversationCoordinator;
import me.zhengjie.agent.application.conversation.ConversationExecutionContext;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * v2 聊天应用服务入口。
 *
 * <p>本类只负责将内部聊天请求映射到应用层命令；业务判断、工具选择和回答组装均由
 * {@link ConversationCoordinator} 路由到能力处理器。</p>
 */
@Service
public class MealPlanChatServiceImpl implements MealPlanChatService {

    private static final String REQUEST_ID_KEY = "requestId";
    private final ConversationCoordinator coordinator;

    public MealPlanChatServiceImpl(ConversationCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * 将 v2 控制器转换后的聊天请求交给统一协调器。
     *
     * @param request v2 API 映射后的内部聊天请求
     * @return 结构化聊天响应
     */
    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        String sessionId = request == null ? null : request.getSessionId();
        String requestId = MDC.get(REQUEST_ID_KEY);
        return coordinator.coordinate(
            new ChatCommand(request),
            new ConversationExecutionContext(requestId, sessionId)
        ).response();
    }
}
