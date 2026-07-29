package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.SemanticCapabilityCatalog;
import me.zhengjie.agent.analysis.domain.*;
import me.zhengjie.agent.application.conversation.ConversationExecutionContext;
import me.zhengjie.agent.capability.CapabilityHandler;
import me.zhengjie.agent.capability.CapabilityHandlerRegistry;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** 验证已登记集合余额帧只能编译为固定的受控计划。 */
class MultiIntentPlanningServiceTest {
    @Test
    void compilesActiveCustomerBalanceFrame() {
        SemanticScope scope = new SemanticScope();
        scope.setType(SemanticScope.Type.CONTEXT_REFERENCE); scope.setRequiredKind(ContextHandleKind.ENTITY_SET); scope.setResolvedHandleId("ctx-active"); scope.setResolvedDefinitionId("AGENT_ACTIVE_CUSTOMER_V1");
        SemanticRequestFrame frame = new SemanticRequestFrame();
        frame.setGoal(SemanticGoal.QUERY); frame.setTargetEntity(SemanticEntityType.CUSTOMER); frame.setScope(scope);
        frame.setMeasures(List.of(AgentQueryMetric.MEAL_BALANCE));
        frame.setOperations(List.of(SemanticOperation.PROJECT, SemanticOperation.GROUP)); frame.setOutputShape(SemanticOutputShape.DETAIL_LIST);
        ConversationUnderstandingResult result = new ConversationUnderstandingResult(); result.setFrames(List.of(frame));
        assertEquals(1, new MultiIntentPlanningService().plan(result).size());
        assertEquals(AgentQueryAction.BREAKDOWN, new MultiIntentPlanningService().plan(result).get(0).getAction());
        assertEquals(AgentQueryMetric.ACTIVE_CUSTOMER_MEAL_BALANCE_DETAIL, new MultiIntentPlanningService().plan(result).get(0).getMetrics().get(0));
    }

    @Test
    void rejectsUnregisteredCombination() {
        SemanticRequestFrame frame = new SemanticRequestFrame();
        frame.setGoal(SemanticGoal.EXPLAIN); frame.setTargetEntity(SemanticEntityType.CUSTOMER); frame.setOutputShape(SemanticOutputShape.DETAIL);
        ConversationUnderstandingResult result = new ConversationUnderstandingResult(); result.setFrames(List.of(frame));
        assertTrue(new MultiIntentPlanningService().plan(result).isEmpty());
    }

    @Test
    void compilesCustomerOrderListFrame() {
        SemanticRequestFrame frame = new SemanticRequestFrame();
        frame.setGoal(SemanticGoal.QUERY); frame.setTargetEntity(SemanticEntityType.ORDER); frame.setOutputShape(SemanticOutputShape.DETAIL_LIST);
        frame.setOperations(List.of(SemanticOperation.LIST));
        ConversationUnderstandingResult result = new ConversationUnderstandingResult(); result.setFrames(List.of(frame));
        assertEquals("listOrders", new MultiIntentPlanningService().plan(result).get(0).getToolNames().get(0));
    }

    @Test
    void compilesTemporaryCapabilityThroughHandlerWithoutCentralPlannerBranch() {
        SemanticRequestFrame frame = new SemanticRequestFrame();
        frame.setGoal(SemanticGoal.QUERY);
        frame.setTargetEntity(SemanticEntityType.ORDER);
        frame.setOutputShape(SemanticOutputShape.SUMMARY);
        frame.setOperations(List.of(SemanticOperation.COUNT));

        SemanticCapabilityCatalog catalog = new SemanticCapabilityCatalog();
        catalog.setCatalogVersion("test");
        SemanticCapabilityCatalog.SemanticFrameConstraint constraint =
            new SemanticCapabilityCatalog.SemanticFrameConstraint(
                SemanticGoal.QUERY, SemanticEntityType.ORDER, Set.of("EXPLICIT"),
                Set.of(), Set.of(), Set.of(SemanticOperation.COUNT),
                Set.of(SemanticOutputShape.SUMMARY));
        catalog.setCapabilities(List.of(new SemanticCapabilityCatalog.CapabilityDefinition(
            "TEST_ORDER_COUNT_V1", "测试订单计数", constraint, Set.of(),
            Set.of("customerOrder:list"), "TEST_ORDER_COUNT_PROFILE_V1",
            SemanticCapabilityCatalog.RiskLevel.INTERNAL)));

        CapabilityHandler handler = new CapabilityHandler() {
            public String handlerId() { return "test-order-count"; }
            public Set<String> plannerProfiles() { return Set.of("TEST_ORDER_COUNT_PROFILE_V1"); }
            public AgentQueryPlan compile(String profile, SemanticRequestFrame request,
                                          ConversationExecutionContext context) {
                AgentQueryPlan plan = new AgentQueryPlan();
                plan.setDomain(AgentQueryDomain.ORDER);
                plan.setAction(AgentQueryAction.SUMMARY);
                plan.setToolNames(List.of("testOrderCount"));
                return plan;
            }
        };
        CapabilityHandlerRegistry registry = new CapabilityHandlerRegistry(List.of(handler), catalog);
        ConversationUnderstandingResult understanding = new ConversationUnderstandingResult();
        understanding.setFrames(List.of(frame));

        List<AgentQueryPlan> plans = new MultiIntentPlanningService(catalog, registry).plan(understanding);

        assertEquals(1, plans.size());
        assertEquals("testOrderCount", plans.get(0).getToolNames().get(0));
    }
}
