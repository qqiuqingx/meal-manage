package me.zhengjie.agent.application.conversation;

/** 可扩展的会话处理器。新增能力以 Handler 形式加入，而不是向协调器添加业务分支。 */
public interface ConversationHandler {
    String handlerId();
    boolean supports(ChatCommand command, ConversationExecutionContext context);
    ChatResult handle(ChatCommand command, ConversationExecutionContext context);
}
