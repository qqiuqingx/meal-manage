package me.zhengjie.agent.observability;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisTraceCollectorTest {

    @Test
    void shouldCacheRepeatedToolCallAndEmitCacheHit() {
        DiagnosisTraceCollector collector = new DiagnosisTraceCollector();
        collector.openSession(8);

        DiagnosisTraceCollector.ToolDecision first = collector.beforeToolCall("getCustomerProfile", "abc123");
        assertTrue(first.shouldProceed());
        collector.recordToolSuccess("getCustomerProfile", "abc123", Map.of("customerId", 1001L), 12L);

        DiagnosisTraceCollector.ToolDecision second = collector.beforeToolCall("getCustomerProfile", "abc123");

        assertFalse(second.shouldProceed());
        assertTrue(second.cached());
        assertEquals(2, collector.snapshotToolSummary().size());
        assertEquals("TOOL_CACHE_HIT", collector.snapshotToolSummary().get(1).getEventType());
        collector.closeSession();
    }

    @Test
    void shouldRejectWhenBudgetExceeded() {
        DiagnosisTraceCollector collector = new DiagnosisTraceCollector();
        collector.openSession(1);

        assertTrue(collector.beforeToolCall("getCustomerProfile", "first").shouldProceed());
        collector.recordToolSuccess("getCustomerProfile", "first", Map.of("customerId", 1001L), 10L);
        DiagnosisTraceCollector.ToolDecision rejected = collector.beforeToolCall("listCustomerOrders", "second");

        assertFalse(rejected.shouldProceed());
        assertEquals("tool call budget exceeded", rejected.rejectedException().getMessage());
        assertTrue(collector.shouldFallback());
        assertEquals("工具调用超出预算，诊断数据不完整，需人工核对。", collector.fallbackReason());
        collector.closeSession();
    }

    @Test
    void shouldMarkFallbackWhenCriticalToolFails() {
        DiagnosisTraceCollector collector = new DiagnosisTraceCollector();
        collector.openSession(8);

        assertTrue(collector.beforeToolCall("getMealPlan", "digest-1").shouldProceed());
        collector.recordToolFailure("getMealPlan", "digest-1", 15L, new IllegalStateException("broken"));

        assertTrue(collector.shouldFallback());
        assertEquals("关键工具调用失败，诊断数据不完整，需人工核对。", collector.fallbackReason());
        assertEquals("IllegalStateException", collector.snapshotToolSummary().get(0).getErrorType());
        collector.closeSession();
    }

    @Test
    void shouldRecordModelRoundEvents() {
        DiagnosisTraceCollector collector = new DiagnosisTraceCollector();
        collector.openSession(8);

        collector.recordModelRoundStart(1);
        collector.recordModelRoundCompleted(1, true, 2, "getMealPlan,listCustomerOrders", 30L);

        assertEquals(2, collector.snapshotTrace().size());
        assertEquals("MODEL_ROUND_START", collector.snapshotTrace().get(0).getEventType());
        assertEquals("MODEL_ROUND_COMPLETED", collector.snapshotTrace().get(1).getEventType());
        assertEquals("getMealPlan,listCustomerOrders", collector.snapshotTrace().get(1).getToolNames());
        collector.closeSession();
    }
}
