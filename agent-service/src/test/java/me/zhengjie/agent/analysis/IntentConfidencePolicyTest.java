package me.zhengjie.agent.analysis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 防止模型自报高分绕过敏感集合明细的引用约束。 */
class IntentConfidencePolicyTest {
    @Test
    void sensitiveDetailNeedsUniqueReference() {
        IntentConfidencePolicy policy = new IntentConfidencePolicy();
        assertEquals(IntentConfidencePolicy.Decision.LOW,
            policy.evaluate(new IntentConfidencePolicy.Input(.99D, true, false, false, true)));
        assertEquals(IntentConfidencePolicy.Decision.HIGH,
            policy.evaluate(new IntentConfidencePolicy.Input(.99D, true, false, true, true)));
    }
}
