package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.ContextHandleKind;
import me.zhengjie.agent.analysis.domain.SemanticGoal;
import me.zhengjie.agent.analysis.domain.SemanticOperation;
import me.zhengjie.agent.analysis.domain.SemanticOutputShape;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.analysis.domain.SemanticScope;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 目录必须包含唯一能力 ID 和固定规划 profile。 */
class SemanticCapabilityCatalogLoaderTest {
    @Test
    void loadsCapabilityCatalog() {
        SemanticCapabilityCatalog catalog = new SemanticCapabilityCatalogLoader().load();
        assertEquals("1.0", catalog.getCatalogVersion());
        assertEquals(5, catalog.getCapabilities().size());
    }

    @Test
    void matchesOnlyRegisteredBalanceBreakdownCombination() {
        SemanticScope scope = new SemanticScope();
        scope.setType(SemanticScope.Type.CONTEXT_REFERENCE);
        scope.setRequiredKind(ContextHandleKind.ENTITY_SET);
        scope.setResolvedDefinitionId("AGENT_ACTIVE_CUSTOMER_V1");
        SemanticRequestFrame frame = new SemanticRequestFrame();
        frame.setGoal(SemanticGoal.QUERY); frame.setTargetEntity(SemanticEntityType.CUSTOMER); frame.setScope(scope);
        frame.setMeasures(List.of(AgentQueryMetric.MEAL_BALANCE));
        frame.setOperations(List.of(SemanticOperation.PROJECT, SemanticOperation.GROUP));
        frame.setOutputShape(SemanticOutputShape.DETAIL_LIST);
        SemanticCapabilityCatalog catalog = new SemanticCapabilityCatalogLoader().load();
        assertEquals("CUSTOMER_MEAL_BALANCE_BREAKDOWN_V1", catalog.findMatching(frame).orElseThrow().get("capabilityId"));
        scope.setResolvedDefinitionId("UNREGISTERED_CUSTOMER_SET");
        assertTrue(catalog.findMatching(frame).isEmpty());
        scope.setResolvedDefinitionId("AGENT_ACTIVE_CUSTOMER_V1");
        frame.setMeasures(List.of(AgentQueryMetric.REFUND_COUNT));
        assertTrue(catalog.findMatching(frame).isEmpty());
    }
}
