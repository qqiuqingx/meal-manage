package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.infrastructure.observability.AgentMdcScope;
import org.springframework.stereotype.Component;

import java.util.List;

/** 仅负责选择受控 Handler；不包含具体意图、工具或业务查询分支。 */
@Component
public class ConversationCoordinator {
    private final List<ConversationHandler> handlers;

    public ConversationCoordinator(List<ConversationHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    /**
     * 将命令路由至唯一支持它的处理器。
     *
     * @param command 已完成外部 DTO 映射的聊天命令
     * @param context 本轮可信执行上下文
     * @return 处理器产生的结构化响应
     */
    public ChatResult coordinate(ChatCommand command, ConversationExecutionContext context) {
        List<ConversationHandler> matched = handlers.stream()
            .filter(handler -> handler.supports(command, context)).toList();
        if (matched.isEmpty()) throw new IllegalStateException("No conversation handler available");
        if (matched.size() > 1) throw new IllegalStateException("Multiple conversation handlers matched: "
            + matched.stream().map(ConversationHandler::handlerId).sorted().toList());
        ConversationHandler handler = matched.get(0);
        try (AgentMdcScope ignored = AgentMdcScope.put("capabilityId", handler.handlerId())) {
            return handler.handle(command, context);
        }
    }
}
