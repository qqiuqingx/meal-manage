package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.domain.dto.AgentChatRequest;

/** 应用层聊天命令；HTTP DTO 映射完成后才进入协调器。 */
public record ChatCommand(AgentChatRequest request) { }
