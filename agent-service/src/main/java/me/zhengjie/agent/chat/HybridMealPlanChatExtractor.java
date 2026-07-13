package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.domain.dto.IntentClassificationRequest;
import me.zhengjie.agent.domain.dto.IntentClassificationResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 混合聊天抽取器：规则负责槽位和高确定性意图，模型负责上下文依赖意图，失败时回退规则。
 */
@Component
@Primary
public class HybridMealPlanChatExtractor implements MealPlanChatExtractor {

    private final RuleBasedSlotExtractor slotExtractor;
    private final RuleBasedIntentClassifier ruleClassifier;
    private final ObjectProvider<LlmIntentClassifier> llmClassifier;
    private final String mode;

    public HybridMealPlanChatExtractor(RuleBasedSlotExtractor slotExtractor,
                                       RuleBasedIntentClassifier ruleClassifier,
                                       ObjectProvider<LlmIntentClassifier> llmClassifier,
                                       @Value("${agent.chat.intent-classifier.mode:hybrid}") String mode) {
        this.slotExtractor = slotExtractor;
        this.ruleClassifier = ruleClassifier;
        this.llmClassifier = llmClassifier;
        this.mode = mode == null ? "hybrid" : mode.trim().toLowerCase();
    }

    /**
     * 抽取当前消息并按配置选择规则、模型或混合意图结果。
     *
     * @param message 当前用户消息
     * @param existingSlots 会话中已有槽位
     * @return 兼容现有服务的聊天抽取结果
     */
    @Override
    public ChatExtractionResult extract(String message, DiagnosisSlots existingSlots) {
        return extract(message, existingSlots, null);
    }

    /**
     * 抽取当前消息，并将会话阶段、最近轮次和诊断结果状态提供给分类器。
     *
     * @param message 当前用户消息
     * @param existingSlots 会话中已有槽位
     * @param conversationState 当前会话状态
     * @return 兼容现有服务的聊天抽取结果
     */
    @Override
    public ChatExtractionResult extract(String message,
                                        DiagnosisSlots existingSlots,
                                        DiagnosisConversationState conversationState) {
        ChatExtractionResult slots = slotExtractor.extract(message, existingSlots);
        IntentClassificationRequest request = new IntentClassificationRequest();
        request.setUserMessage(message);
        request.setExistingSlots(existingSlots);
        if (conversationState != null) {
            request.setRecentTurns(conversationState.getRecentTurns());
            request.setConversationStage(conversationState.getStage());
            request.setHasLastDiagnosisResult(conversationState.getLastDiagnosisResult() != null);
        }
        request.setRuleIntentCandidate(slots.getIntent() == null ? null : slots.getIntent().name());

        IntentClassificationResult rule = ruleClassifier.classify(request);
        request.setRuleIntentConfidence(rule == null ? 0.0 : rule.getConfidence());
        slots.setRuleIntent(request.getRuleIntentCandidate());
        IntentClassificationResult selected = rule;
        String source = "RULE";
        boolean llmTriggered = false;
        if (shouldUseLlm(rule)) {
            llmTriggered = true;
            LlmIntentClassifier classifier = llmClassifier.getIfAvailable();
            if (classifier != null) {
                IntentClassificationResult llm = classifier.classify(request);
                if (usable(llm)) {
                    selected = llm;
                    source = "LLM";
                }
            }
        }
        if (!usable(selected)) {
            selected = rule;
        }
        if (selected != null && selected.getIntent() != null) {
            ChatIntent selectedIntent = selected.getIntent();
            if (isLegacyBusinessIntent(selectedIntent)) {
                // 旧意图只作为兼容路由线索，对外统一进入业务查询顶层入口。
                slots.setRuleIntent(selectedIntent.name());
                slots.setIntent(ChatIntent.BUSINESS_QUERY);
            } else {
                slots.setIntent(selectedIntent);
            }
            slots.setIntentConfidence(selected.getConfidence());
            slots.setIntentReason(selected.getReason());
        }
        slots.setIntentSource(llmTriggered && "RULE".equals(source) ? "HYBRID" : source);
        slots.setLlmTriggered(llmTriggered);
        return slots;
    }

    private boolean shouldUseLlm(IntentClassificationResult rule) {
        if ("rule_only".equals(mode)) return false;
        if ("llm_only".equals(mode)) return true;
        return rule == null || rule.getIntent() == null || rule.getConfidence() < 0.8;
    }

    private boolean usable(IntentClassificationResult result) {
        return result != null && result.getIntent() != null && result.getConfidence() >= 0.8;
    }

    /** 判断旧版细粒度意图是否应归入顶层只读业务查询入口。 */
    private boolean isLegacyBusinessIntent(ChatIntent intent) {
        return intent == ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY
            || intent == ChatIntent.CUSTOMER_VERIFICATION_QUERY
            || intent == ChatIntent.CUSTOMER_ORDER_QUERY
            || intent == ChatIntent.CUSTOMER_REFUND_QUERY
            || intent == ChatIntent.CUSTOMER_PACKAGE_QUERY
            || intent == ChatIntent.MEAL_PLAN_QUERY
            || intent == ChatIntent.SCHEDULED_MENU_QUERY
            || intent == ChatIntent.MEAL_PLAN_UNVERIFIED_QUERY
            || intent == ChatIntent.MEAL_BALANCE_NO_PLAN_QUERY
            || intent == ChatIntent.MEAL_BALANCE_CHANGE_QUERY
            || intent == ChatIntent.DISH_INGREDIENT_QUERY
            || intent == ChatIntent.DISH_CANDIDATE_QUERY
            || intent == ChatIntent.BUSINESS_RULE_QUERY
            || intent == ChatIntent.OPERATION_STATISTICS_QUERY;
    }
}
