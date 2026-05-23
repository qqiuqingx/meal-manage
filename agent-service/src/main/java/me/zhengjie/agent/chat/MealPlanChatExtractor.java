package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;

/**
 * 聊天抽取器。
 */
public interface MealPlanChatExtractor {

    ChatExtractionResult extract(String message, DiagnosisSlots existingSlots);
}
