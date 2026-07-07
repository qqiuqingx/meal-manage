package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryMealPlanChatSessionStoreTest {

    @Test
    void shouldCreateNewSessionWithDefaultConversationStage() {
        InMemoryMealPlanChatSessionStore store = new InMemoryMealPlanChatSessionStore(
            Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneId.of("UTC")),
            Duration.ofMinutes(30)
        );

        MealPlanChatSession session = store.getOrCreate(null);

        assertNotNull(session.getSessionId());
        assertNotNull(session.getSlots());
        assertNotNull(session.getConversationState());
        assertEquals(DiagnosisConversationState.COLLECTING_SLOTS, session.getConversationState().getStage());
        assertEquals(Instant.parse("2026-05-22T00:00:00Z"), session.getUpdatedAt());
    }

    @Test
    void shouldReturnExistingSessionBeforeTtlExpires() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneId.of("UTC"));
        InMemoryMealPlanChatSessionStore store = new InMemoryMealPlanChatSessionStore(clock, Duration.ofMinutes(30));
        MealPlanChatSession session = store.getOrCreate("session-1");
        session.getSlots().setCustomerCode("C10001");
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        store.save(session);

        MealPlanChatSession loaded = store.getOrCreate("session-1");

        assertEquals("session-1", loaded.getSessionId());
        assertEquals("C10001", loaded.getSlots().getCustomerCode());
        assertEquals(DiagnosisConversationState.DIAGNOSED, loaded.getConversationState().getStage());
    }

    @Test
    void shouldCreateFreshSessionAfterTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-22T00:00:00Z"));
        InMemoryMealPlanChatSessionStore store = new InMemoryMealPlanChatSessionStore(clock, Duration.ofMinutes(30));
        MealPlanChatSession session = store.getOrCreate("session-1");
        session.getSlots().setCustomerCode("C10001");
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        store.save(session);

        clock.instant = Instant.parse("2026-05-22T00:31:00Z");
        MealPlanChatSession loaded = store.getOrCreate("session-1");

        assertEquals("session-1", loaded.getSessionId());
        assertNull(loaded.getSlots().getCustomerCode());
        assertEquals(DiagnosisConversationState.COLLECTING_SLOTS, loaded.getConversationState().getStage());
    }

    @Test
    void shouldResetSessionSlotsAndConversationState() {
        InMemoryMealPlanChatSessionStore store = new InMemoryMealPlanChatSessionStore(
            Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneId.of("UTC")),
            Duration.ofMinutes(30)
        );
        MealPlanChatSession session = store.getOrCreate("session-1");
        session.getSlots().setCustomerId(1001L);
        DiagnosisResponse diagnosis = new DiagnosisResponse();
        diagnosis.setSummary("上次诊断");
        session.getConversationState().addDiagnosisResult(diagnosis);
        store.save(session);

        MealPlanChatSession reset = store.reset("session-1");

        assertEquals("session-1", reset.getSessionId());
        assertNull(reset.getSlots().getCustomerId());
        assertEquals(DiagnosisConversationState.COLLECTING_SLOTS, reset.getConversationState().getStage());
        assertNull(reset.getConversationState().getLastDiagnosisResult());
        assertEquals(0, reset.getConversationState().getRecentDiagnosisResults().size());
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
