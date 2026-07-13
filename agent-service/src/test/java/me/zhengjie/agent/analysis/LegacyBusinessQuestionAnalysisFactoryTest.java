package me.zhengjie.agent.analysis;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.BusinessQueryPlanningService;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 旧业务意图兼容期间也必须经过统一的受控分析和规划协议。 */
class LegacyBusinessQuestionAnalysisFactoryTest {

    @Test
    void shouldPlanVerificationFromCompatibilityAnalysis() {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerId(1001L);
        slots.setMealType("LUNCH");

        AgentQueryPlan plan = new BusinessQueryPlanningService().plan(
            LegacyBusinessQuestionAnalysisFactory.fromIntent(ChatIntent.CUSTOMER_VERIFICATION_QUERY, slots));

        assertEquals(AgentQueryDomain.VERIFICATION, plan.getDomain());
        assertEquals(AgentQueryAction.LIST, plan.getAction());
        assertEquals("listVerifications", plan.getToolNames().get(0));
        assertEquals("LEGACY_COMPATIBILITY", plan.getAnalysisSource());
        assertEquals(1001L, plan.getEntities().getCustomerId());
    }
}
