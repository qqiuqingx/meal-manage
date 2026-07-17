package me.zhengjie.agent.evaluation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 验证评测报告仅输出聚合指标，并正确计算延迟分位。 */
class IntentEvaluationReporterTest {
    @Test
    void summarizesSanitizedObservations() {
        IntentEvaluationReporter reporter = new IntentEvaluationReporter();
        var result = reporter.summarize(List.of(
            new IntentEvaluationReporter.EvaluationObservation(true, true, true, 10),
            new IntentEvaluationReporter.EvaluationObservation(true, false, true, 30),
            new IntentEvaluationReporter.EvaluationObservation(false, true, false, 50)));
        assertEquals(3L, result.get("caseCount")); assertEquals(30L, result.get("p50LatencyMs")); assertEquals(50L, result.get("p95LatencyMs"));
    }
}
