package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;

import java.time.Clock;

/**
 * 旧规则抽取器兼容壳。槽位规则已迁移到 {@link RuleBasedSlotExtractor}，意图编排由混合抽取器负责。
 */
public class RuleBasedMealPlanChatExtractor implements MealPlanChatExtractor {

    private final RuleBasedSlotExtractor slotExtractor;

    public RuleBasedMealPlanChatExtractor() {
        this.slotExtractor = new RuleBasedSlotExtractor();
    }

    RuleBasedMealPlanChatExtractor(Clock clock) {
        this.slotExtractor = new RuleBasedSlotExtractor(clock);
    }

    /**
     * 兼容旧调用，返回规则槽位抽取结果和历史规则意图。
     *
     * @param message 当前用户消息
     * @param existingSlots 已有槽位
     * @return 规则抽取结果
     */
    @Override
    public ChatExtractionResult extract(String message, DiagnosisSlots existingSlots) {
        return slotExtractor.extract(message, existingSlots);
    }
}
