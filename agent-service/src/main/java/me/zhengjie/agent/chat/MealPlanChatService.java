package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;

/**
 * 聊天编排服务。
 */
public interface MealPlanChatService {

    AgentChatResponse chat(AgentChatRequest request);
}
