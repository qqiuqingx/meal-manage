package me.zhengjie.agent.infrastructure.llm;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 纯单元测试覆盖熔断状态转换，不访问任何真实模型。 */
class ProviderHealthTrackerTest {
    @Test
    void shouldOpenAfterThreeRecoverableFailuresAndRecoverAfterProbe() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-30T00:00:00Z"));
        ProviderHealthTracker tracker = new ProviderHealthTracker(clock);
        tracker.recordRecoverableFailure("primary"); tracker.recordRecoverableFailure("primary"); tracker.recordRecoverableFailure("primary");
        assertEquals(ProviderHealthTracker.State.OPEN, tracker.state("primary"));
        assertFalse(tracker.allowRequest("primary"));
        clock.advanceSeconds(30);
        assertTrue(tracker.allowRequest("primary"));
        tracker.recordSuccess("primary");
        assertEquals(ProviderHealthTracker.State.CLOSED, tracker.state("primary"));
    }
    private static final class MutableClock extends Clock {
        private Instant instant; MutableClock(Instant instant) { this.instant = instant; }
        void advanceSeconds(long value) { instant = instant.plusSeconds(value); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
