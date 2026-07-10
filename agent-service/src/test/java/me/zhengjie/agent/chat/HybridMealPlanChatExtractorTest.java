package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.domain.dto.IntentClassificationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridMealPlanChatExtractorTest {

    private final RuleBasedSlotExtractor slots = new RuleBasedSlotExtractor(
        Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
    );

    @Test
    void shouldReturnExplicitCustomerQueryFromRulesWithoutLlm() {
        HybridMealPlanChatExtractor extractor = extractor("hybrid", null);

        ChatExtractionResult result = extractor.extract("B2201 核销了多少餐", new DiagnosisSlots());

        assertEquals(ChatIntent.CUSTOMER_VERIFICATION_QUERY, result.getIntent());
        assertEquals("RULE", result.getIntentSource());
        assertFalse(result.isLlmTriggered());
    }

    @Test
    void shouldUseHighConfidenceLlmResultForContextDependentQuestion() {
        LlmIntentClassifier llm = new StubLlmClassifier(
            new IntentClassificationResult(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, 0.93, "指代最近客户", false)
        );
        HybridMealPlanChatExtractor extractor = extractor("hybrid", llm);
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("B2201");

        ChatExtractionResult result = extractor.extract("他一共多少餐", existing);

        assertEquals(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, result.getIntent());
        assertEquals("LLM", result.getIntentSource());
        assertTrue(result.isLlmTriggered());
    }

    @Test
    void shouldFallbackToRuleWhenLlmResultIsLowConfidence() {
        LlmIntentClassifier llm = new StubLlmClassifier(
            new IntentClassificationResult(ChatIntent.DIAGNOSE, 0.4, "不确定", true)
        );
        HybridMealPlanChatExtractor extractor = extractor("hybrid", llm);
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("B2201");

        ChatExtractionResult result = extractor.extract("他一共多少餐", existing);

        assertEquals(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, result.getIntent());
        assertEquals("HYBRID", result.getIntentSource());
        assertTrue(result.isLlmTriggered());
    }

    @SuppressWarnings("unchecked")
    private HybridMealPlanChatExtractor extractor(String mode, LlmIntentClassifier llm) {
        ObjectProvider<LlmIntentClassifier> provider = new FixedProvider(llm);
        return new HybridMealPlanChatExtractor(slots, new RuleBasedIntentClassifier(), provider, mode);
    }

    private static class StubLlmClassifier extends LlmIntentClassifier {
        private final IntentClassificationResult result;

        private StubLlmClassifier(IntentClassificationResult result) {
            this.result = result;
        }

        @Override
        public IntentClassificationResult classify(me.zhengjie.agent.domain.dto.IntentClassificationRequest request) {
            return result;
        }
    }

    private static class FixedProvider implements ObjectProvider<LlmIntentClassifier> {
        private final LlmIntentClassifier classifier;

        private FixedProvider(LlmIntentClassifier classifier) {
            this.classifier = classifier;
        }

        @Override
        public LlmIntentClassifier getObject(Object... args) {
            return classifier;
        }

        @Override
        public Stream<LlmIntentClassifier> stream() {
            return Stream.ofNullable(classifier);
        }
    }
}
