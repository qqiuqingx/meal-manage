package me.zhengjie.agent.query.domain;

import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.BusinessQueryPlanner;
import me.zhengjie.agent.tool.ToolCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessResponseTypeCatalogTest {

    @Test
    void everyCatalogToolMustUseTheAuthoritativeToolCatalog() {
        BusinessResponseTypeCatalog.definitionsView().values().forEach(definition ->
            definition.toolNames().forEach(tool ->
                assertTrue(ToolCatalog.isRegistered(tool),
                    () -> definition.responseType() + " references unknown tool " + tool)));
    }

    @Test
    void plannerMustResolveCatalogMetadataWithoutResponseTypeBranches() {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerId(3303L);

        AgentQueryPlan plan = new BusinessQueryPlanner()
            .plan(BusinessResponseTypeCatalog.MEAL_PLAN, slots);

        assertNotNull(plan);
        assertEquals(AgentQueryDomain.MEAL_PLAN, plan.getDomain());
        assertEquals(AgentQueryAction.LIST, plan.getAction());
        assertEquals(java.util.List.of(ToolCatalog.LIST_MEAL_PLANS),
            plan.getToolNames());
        assertEquals(BusinessQueryTarget.CUSTOMER_MEAL_PLAN,
            BusinessResponseTypeCatalog.find(BusinessResponseTypeCatalog.MEAL_PLAN)
                .orElseThrow().queryTarget());
    }

    @Test
    void unknownResponseTypeMustNotFallbackToCustomerOverview() {
        assertFalse(BusinessResponseTypeCatalog.find("BUSINESS_QUERY_UNKNOWN").isPresent());
        assertEquals(null, new BusinessQueryPlanner()
            .plan("BUSINESS_QUERY_UNKNOWN", new DiagnosisSlots()));
    }
}
