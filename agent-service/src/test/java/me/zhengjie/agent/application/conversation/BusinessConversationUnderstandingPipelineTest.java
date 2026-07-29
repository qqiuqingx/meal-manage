package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.analysis.BusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.BusinessTemporalResolver;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.chat.MealPlanChatSession;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.config.BusinessTimeProperties;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessConversationUnderstandingPipelineTest {

    @Test
    void deterministicSessionSlotsMustOverrideAnalyzerValues() {
        BusinessQuestionAnalysis analyzed = analysis();
        analyzed.getEntities().setCustomerCode("MODEL-CODE");
        analyzed.getFilters().setRecordDate("2026-07-01");
        BusinessConversationUnderstandingPipeline pipeline =
            pipeline((question, slots) -> analyzed);
        MealPlanChatSession session = session();
        session.getSlots().setCustomerCode("B3303");
        session.getSlots().setRecordDate("2026-07-29");

        BusinessConversationUnderstandingPipeline.UnderstandingOutcome outcome =
            pipeline.understand(session, "查询客户今天的订单");

        assertFalse(outcome.pendingReused());
        assertEquals("B3303", outcome.analysis().getEntities().getCustomerCode());
        assertEquals("2026-07-29", outcome.analysis().getFilters().getRecordDate());
    }

    @Test
    void pureSlotReplyMustResumePendingWithoutCallingAnalyzerAgain() {
        AtomicInteger calls = new AtomicInteger();
        BusinessQuestionAnalyzer analyzer = (question, slots) -> {
            calls.incrementAndGet();
            return analysis();
        };
        BusinessConversationUnderstandingPipeline pipeline = pipeline(analyzer);
        MealPlanChatSession session = session();
        BusinessQuestionAnalysis pendingAnalysis = analysis();
        pendingAnalysis.setRequiresClarification(true);
        pipeline.savePendingContext(session, pendingAnalysis, List.of("recordDate"));
        session.getSlots().setRecordDate("2026-07-30");

        BusinessConversationUnderstandingPipeline.UnderstandingOutcome outcome =
            pipeline.understand(session, "2026-07-30");

        assertTrue(outcome.pendingReused());
        assertFalse(outcome.analysis().isRequiresClarification());
        assertEquals("PENDING_CONTEXT", outcome.analysis().getSource());
        assertEquals("2026-07-30", outcome.analysis().getFilters().getRecordDate());
        assertEquals(0, calls.get());
        assertNotNull(pipeline.semanticTrace(outcome.analysis(), true));
        assertTrue(pipeline.semanticTrace(outcome.analysis(), true).isPendingContextReused());
    }

    private BusinessConversationUnderstandingPipeline pipeline(BusinessQuestionAnalyzer analyzer) {
        AgentProperties properties = new AgentProperties();
        properties.getChat().getBusinessSemantic().setPendingContextEnabled(true);
        properties.getChat().getBusinessSemantic().setPendingContextTtlMinutes(30);
        BusinessTemporalResolver resolver = new BusinessTemporalResolver(
            Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
            new BusinessTimeProperties());
        return new BusinessConversationUnderstandingPipeline(analyzer, resolver, properties);
    }

    private MealPlanChatSession session() {
        MealPlanChatSession session = new MealPlanChatSession();
        session.setSessionId("session-1");
        return session;
    }

    private BusinessQuestionAnalysis analysis() {
        BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
        analysis.setSource("RULE");
        analysis.setConfidence(.95D);
        analysis.setDomains(List.of(AgentQueryDomain.ORDER));
        analysis.setEntities(new AgentEntityReference());
        analysis.setFilters(new AgentQueryFilters());
        return analysis;
    }
}
