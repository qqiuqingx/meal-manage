package me.zhengjie.agent.infrastructure.llm;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 内存熔断器：连续三次可恢复失败打开 30 秒，随后只允许一次 half-open 探测。 */
public class ProviderHealthTracker {
    public enum State { CLOSED, OPEN, HALF_OPEN }
    private final Clock clock;
    private final Map<String, Entry> entries = new HashMap<>();
    public ProviderHealthTracker() { this(Clock.systemUTC()); }
    public ProviderHealthTracker(Clock clock) { this.clock = clock; }
    public synchronized boolean allowRequest(String providerId) {
        Entry entry = entry(providerId);
        if (entry.state == State.CLOSED) return true;
        if (entry.state == State.OPEN && !clock.instant().isBefore(entry.openUntil)) {
            entry.state = State.HALF_OPEN;
        }
        if (entry.state == State.HALF_OPEN && !entry.probeInFlight) { entry.probeInFlight = true; return true; }
        return false;
    }
    public synchronized void recordSuccess(String providerId) {
        Entry entry = entry(providerId); entry.state = State.CLOSED; entry.failures = 0; entry.probeInFlight = false; entry.openUntil = null;
    }
    public synchronized void recordRecoverableFailure(String providerId) {
        Entry entry = entry(providerId); entry.probeInFlight = false; entry.failures++;
        if (entry.state == State.HALF_OPEN || entry.failures >= 3) { entry.state = State.OPEN; entry.openUntil = clock.instant().plusSeconds(30); }
    }
    public synchronized State state(String providerId) { return entry(providerId).state; }
    private Entry entry(String providerId) { return entries.computeIfAbsent(providerId, ignored -> new Entry()); }
    private static final class Entry { private State state = State.CLOSED; private int failures; private boolean probeInFlight; private Instant openUntil; }
}
