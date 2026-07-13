package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 业务问题分析只能映射到固定领域、动作和白名单工具。 */
class BusinessQueryPlanningServiceTest {
    private final BusinessQueryPlanningService service = new BusinessQueryPlanningService();

    @Test
    void shouldPlanCustomerOrderAndHistoryDomainsWithoutModelProvidedTools() {
        BusinessQuestionAnalysis customer = analysis(AgentQueryDomain.CUSTOMER);
        customer.getEntities().setCustomerCode("B3303");
        assertPlan(customer, AgentQueryAction.OVERVIEW, "customerOverview");

        BusinessQuestionAnalysis order = analysis(AgentQueryDomain.ORDER);
        order.getEntities().setOrderCode("O3303");
        assertPlan(order, AgentQueryAction.DETAIL, "orderDetail");

        assertPlan(analysis(AgentQueryDomain.VERIFICATION), AgentQueryAction.LIST, "listVerifications");
        assertPlan(analysis(AgentQueryDomain.REFUND), AgentQueryAction.LIST, "listRefunds");
    }

    @Test
    void shouldRequireClarificationWhenAnalysisCannotExpressRequiredStableReference() {
        assertNull(service.plan(analysis(AgentQueryDomain.PACKAGE)));
        assertNull(service.plan(analysis(AgentQueryDomain.BUSINESS_RULE)));
    }

    private BusinessQuestionAnalysis analysis(AgentQueryDomain domain) {
        BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
        analysis.setDomains(List.of(domain));
        analysis.setEntities(new AgentEntityReference());
        analysis.setConfidence(0.95D);
        return analysis;
    }

    private void assertPlan(BusinessQuestionAnalysis analysis, AgentQueryAction action, String tool) {
        var plan = service.plan(analysis);
        assertEquals(action, plan.getAction());
        assertEquals(List.of(tool), plan.getToolNames());
        assertEquals("RULE", plan.getAnalysisSource());
    }
}
