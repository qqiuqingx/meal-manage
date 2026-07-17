package me.zhengjie.agent.evaluation;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 基础案例扩展后必须达到评测计划规定的 300 条，且不能修改期望结构。 */
class IntentUnderstandingCaseGeneratorTest {
    @Test
    void expandsTwentyBasesToThreeHundredVariants() {
        List<Map<String, Object>> bases = java.util.stream.IntStream.range(0, 20).mapToObj(index -> Map.<String, Object>of(
            "id", "base-" + index, "turns", List.of(Map.of("user", "他们分别还剩多少餐", "expected", Map.of("targetEntity", "CUSTOMER"))))).toList();
        List<Map<String, Object>> variants = new IntentUnderstandingCaseGenerator().expand(bases);
        assertEquals(300, variants.size());
        assertEquals(300, variants.stream().map(item -> item.get("id")).distinct().count());
        Map<?, ?> firstTurn = (Map<?, ?>) ((List<?>) variants.get(0).get("turns")).get(0);
        Map<?, ?> expected = (Map<?, ?>) firstTurn.get("expected");
        assertEquals("CUSTOMER", expected.get("targetEntity"));
    }
}
