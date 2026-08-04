package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.IntentClassificationRequest;
import me.zhengjie.agent.domain.dto.IntentClassificationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedIntentClassifierTest {

    private final RuleBasedIntentClassifier classifier = new RuleBasedIntentClassifier();

    @Test
    void shouldKeepExplicitCustomerQueriesAsLowConfidenceBusinessCandidates() {
        IntentClassificationRequest request = request("B2201 核销了多少餐", ChatIntent.CUSTOMER_VERIFICATION_QUERY);
        IntentClassificationResult result = classifier.classify(request);

        assertEquals(ChatIntent.CUSTOMER_VERIFICATION_QUERY, result.getIntent());
        assertTrue(result.getConfidence() <= 0.65);
        assertTrue(result.isFallbackSuggested());
    }

    @Test
    void shouldLowerConfidenceForContextDependentQuestion() {
        IntentClassificationRequest request = request("他一共多少餐", ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY);
        IntentClassificationResult result = classifier.classify(request);

        assertEquals(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, result.getIntent());
        assertTrue(result.getConfidence() < 0.8);
        assertTrue(result.isFallbackSuggested());
    }

    @Test
    void shouldClassifyControlCommandsDirectly() {
        assertEquals(ChatIntent.RESET, classifier.classify(request("清空会话", null)).getIntent());
        assertEquals(ChatIntent.RETRY, classifier.classify(request("重新排查", null)).getIntent());
    }

    /** “下单”出现在时间查询中属于订单只读追问，不能被写操作规则误杀。 */
    @Test
    void shouldKeepOrderTimeFollowUpAsReadOnlyQuery() {
        IntentClassificationResult result = classifier.classify(
            request("他们分别是什么时候下单的", ChatIntent.CUSTOMER_ORDER_QUERY));

        assertEquals(ChatIntent.CUSTOMER_ORDER_QUERY, result.getIntent());
        assertTrue(result.isFallbackSuggested());
    }

    /** 明确要求创建订单时仍必须拒绝为只读 Agent 的越界操作。 */
    @Test
    void shouldRejectExplicitOrderCreation() {
        assertEquals(ChatIntent.OUT_OF_SCOPE,
            classifier.classify(request("帮我给这个客户下单", ChatIntent.CUSTOMER_ORDER_QUERY)).getIntent());
    }

    private IntentClassificationRequest request(String message, ChatIntent intent) {
        IntentClassificationRequest request = new IntentClassificationRequest();
        request.setUserMessage(message);
        request.setRuleIntentCandidate(intent == null ? null : intent.name());
        return request;
    }
}
