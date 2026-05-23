package me.zhengjie.agent.chat;

/**
 * 聊天会话存储。
 */
public interface MealPlanChatSessionStore {

    MealPlanChatSession getOrCreate(String sessionId);

    void save(MealPlanChatSession session);

    MealPlanChatSession reset(String sessionId);
}
