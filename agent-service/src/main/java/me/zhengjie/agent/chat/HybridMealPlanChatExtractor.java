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
        if (conversationState != null && conversationState.getPendingBusinessQueryContext() != null && isPureSlotReply(message)) {
            slots.setIntent(ChatIntent.BUSINESS_QUERY);
            slots.setRuleIntent("PENDING_CONTEXT_SLOT");
            slots.setIntentConfidence(1.0D);
            slots.setIntentReason("待执行查询纯槽位补充");
            slots.setIntentSource("PENDING_CONTEXT");
            slots.setLlmTriggered(false);
            return slots;
        }
        if (isContextCorrection(message, conversationState)) {
            slots.setIntent(ChatIntent.BUSINESS_QUERY);
            slots.setRuleIntent("CONTEXT_CORRECTION");
            slots.setIntentConfidence(0.0D);
            slots.setIntentReason("检测到上一轮业务查询结果纠错信号");
            slots.setIntentSource("HYBRID");
            slots.setLlmTriggered(true);
            return slots;
        }
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
        if (isBusinessSemanticCandidate(rule)) {
            // 普通业务问题直接进入 BusinessQuestionAnalyzer，避免重复意图模型成为前置失败点。
            source = "SEMANTIC";
        } else if (shouldUseLlm(rule)) {
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
            if (selectedIntent == ChatIntent.DIAGNOSE || isLegacyBusinessIntent(selectedIntent)) {
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

    /** 判断规则结果是否只是业务语义候选；该类问题应由统一业务分析器理解完整语义。 */
    private boolean isBusinessSemanticCandidate(IntentClassificationResult rule) {
        return rule != null && (rule.getIntent() == ChatIntent.BUSINESS_QUERY || isLegacyBusinessIntent(rule.getIntent()));
    }

    private boolean shouldUseLlm(IntentClassificationResult rule) {
        if ("rule_only".equals(mode)) return false;
        if ("llm_only".equals(mode)) return true;
        return rule == null || rule.getIntent() == null || rule.getConfidence() < 0.8;
    }

    private boolean usable(IntentClassificationResult result) {
        return result != null && result.getIntent() != null && result.getConfidence() >= 0.8;
    }

    /** 仅以粗粒度否定信号触发语义分析，不在规则层决定纠错对象。 */
    private boolean isContextCorrection(String message, DiagnosisConversationState state) {
        if (state == null || state.getLastBusinessQueryContext() == null || message == null) return false;
        String text = message.trim();
        return text.matches(".*(不对|不应该|查错|怎么全是|明显|全是米饭|主食列表).*" );
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

    /** 允许多个确定性槽位组合续接 Pending；存在剩余业务文本时必须重新判断领域。 */
    private boolean isPureSlotReply(String message) {
        if (message == null) return false;
        String text = message.trim();
        if (text.isEmpty()) return false;
        String remainder = text.replaceAll("今天|今日|昨天|昨日|明天|明日|本周|这周|下周|早餐|午餐|晚餐", "")
            .replaceAll("\\d{4}-\\d{2}-\\d{2}", "").replaceAll("(?i)[A-Z]\\d{3,}", "").replaceAll("\\d{1,18}", "")
            .replaceAll("[，,、/\\s]+", "");
        return remainder.isEmpty();
    }
}
