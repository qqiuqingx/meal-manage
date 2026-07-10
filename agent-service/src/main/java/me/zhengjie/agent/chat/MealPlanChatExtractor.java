package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;

/**
 * 聊天抽取器。
 */
public interface MealPlanChatExtractor {

    ChatExtractionResult extract(String message, DiagnosisSlots existingSlots);

    /**
     * 使用会话阶段、最近轮次和最近诊断结果进行抽取；旧实现默认忽略扩展上下文。
     *
     * @param message 当前用户消息
     * @param existingSlots 已有槽位
     * @param conversationState 会话上下文
     * @return 聊天抽取结果
     */
    default ChatExtractionResult extract(String message,
                                         DiagnosisSlots existingSlots,
                                         DiagnosisConversationState conversationState) {
        return extract(message, existingSlots);
    }
}
