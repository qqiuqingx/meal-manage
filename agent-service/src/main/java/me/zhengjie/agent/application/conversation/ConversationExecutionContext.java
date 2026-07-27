package me.zhengjie.agent.application.conversation;

/** 本轮执行的可信元数据；不承载原始 HTTP 请求或持久化实现。 */
public record ConversationExecutionContext(String requestId, String sessionId) { }
