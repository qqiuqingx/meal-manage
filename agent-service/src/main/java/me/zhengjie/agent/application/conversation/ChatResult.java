package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.domain.dto.AgentChatResponse;

/** 协调器的结构化结果，为后续 ConversationPatch 和审计摘要保留边界。 */
public record ChatResult(AgentChatResponse response) { }
