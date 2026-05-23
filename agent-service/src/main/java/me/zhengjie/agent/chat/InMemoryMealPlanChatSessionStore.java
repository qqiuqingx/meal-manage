package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存的会话存储，默认 30 分钟过期。
 */
@Component
public class InMemoryMealPlanChatSessionStore implements MealPlanChatSessionStore {

    private final ConcurrentMap<String, MealPlanChatSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public InMemoryMealPlanChatSessionStore() {
        this(Clock.systemDefaultZone(), Duration.ofMinutes(30));
    }

    InMemoryMealPlanChatSessionStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public MealPlanChatSession getOrCreate(String sessionId) {
        String resolvedSessionId = isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId.trim();
        MealPlanChatSession existing = sessions.get(resolvedSessionId);
        if (existing != null && !expired(existing)) {
            return existing;
        }
        MealPlanChatSession created = newSession(resolvedSessionId);
        sessions.put(resolvedSessionId, created);
        return created;
    }

    @Override
    public void save(MealPlanChatSession session) {
        session.setUpdatedAt(clock.instant());
        sessions.put(session.getSessionId(), session);
    }

    @Override
    public MealPlanChatSession reset(String sessionId) {
        String resolvedSessionId = isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId.trim();
        MealPlanChatSession session = newSession(resolvedSessionId);
        sessions.put(resolvedSessionId, session);
        return session;
    }

    private MealPlanChatSession newSession(String sessionId) {
        MealPlanChatSession session = new MealPlanChatSession();
        session.setSessionId(sessionId);
        session.setSlots(new DiagnosisSlots());
        session.setUpdatedAt(clock.instant());
        return session;
    }

    private boolean expired(MealPlanChatSession session) {
        Instant updatedAt = session.getUpdatedAt();
        return updatedAt == null || updatedAt.plus(ttl).isBefore(clock.instant());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
