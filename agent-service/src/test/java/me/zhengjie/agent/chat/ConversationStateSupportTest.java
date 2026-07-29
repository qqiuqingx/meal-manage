package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConversationStateSupportTest {
    private final ConversationStateSupport support = new ConversationStateSupport();

    @Test
    void copiesAndMergesSlotsWithoutSharingMutableMaps() {
        DiagnosisSlots persisted = new DiagnosisSlots();
        persisted.setCustomerCode("C1001");
        persisted.setRecordDate("2026-07-29");
        persisted.setSlotConfidence(Map.of("customerCode", "1.0"));
        DiagnosisSlots incremental = new DiagnosisSlots();
        incremental.setMealType("LUNCH");

        DiagnosisSlots copy = support.copy(persisted);
        DiagnosisSlots merged = support.mergeSlots(copy, incremental);

        assertNotSame(persisted, merged);
        assertNotSame(persisted.getSlotConfidence(), merged.getSlotConfidence());
        assertEquals("C1001", merged.getCustomerCode());
        assertEquals("2026-07-29", merged.getRecordDate());
        assertEquals("LUNCH", merged.getMealType());
    }

    @Test
    void restoresTrustedBusinessContextsAndRecordsIndependentTurnSnapshot() {
        MealPlanChatSession session = new MealPlanChatSession();
        session.setSessionId("session-state");
        session.getSlots().setCustomerCode("C1001");
        PendingBusinessQueryContext pending = new PendingBusinessQueryContext();
        LastBusinessQueryContext last = new LastBusinessQueryContext();
        AgentChatRequest request = new AgentChatRequest();
        request.setPendingBusinessQueryContext(pending);
        request.setLastBusinessQueryContext(last);

        support.hydrateBusinessContexts(session, request);
        ChatExtractionResult extraction = new ChatExtractionResult();
        extraction.setIntent(ChatIntent.BUSINESS_QUERY);
        support.rememberUserTurn(session, "查询订单", extraction);
        session.getSlots().setCustomerCode("C2002");

        assertSame(pending, session.getConversationState().getPendingBusinessQueryContext());
        assertSame(last, session.getConversationState().getLastBusinessQueryContext());
        assertEquals("C1001", session.getConversationState().getRecentTurns().get(0)
            .getSlotsSnapshot().getCustomerCode());
    }
}
