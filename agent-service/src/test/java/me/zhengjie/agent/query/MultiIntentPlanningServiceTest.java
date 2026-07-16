package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.*;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 验证已登记集合余额帧只能编译为固定的受控计划。 */
class MultiIntentPlanningServiceTest {
    @Test
    void compilesActiveCustomerBalanceFrame() {
        SemanticScope scope = new SemanticScope();
        scope.setType(SemanticScope.Type.CONTEXT_REFERENCE); scope.setResolvedHandleId("ctx-active");
        SemanticRequestFrame frame = new SemanticRequestFrame();
        frame.setGoal(SemanticGoal.QUERY); frame.setTargetEntity(SemanticEntityType.CUSTOMER); frame.setScope(scope);
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
}
