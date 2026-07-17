package me.zhengjie.agent.evaluation;

import me.zhengjie.agent.analysis.domain.ContextHandleKind;
import me.zhengjie.agent.analysis.domain.BusinessInteractionMode;
import me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.SemanticScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证评测执行器以合成上下文执行，不会把真实业务数据带入模型评测。 */
class IntentUnderstandingEvaluationRunnerTest {
    @Test
    void evaluatesReferenceCaseWithSyntheticContextHandle() {
        Map<String, Object> turn = Map.of("user", "这些客户还剩几餐", "expected", Map.of(
            "interactionMode", "FOLLOW_UP", "contextHandleKind", "ENTITY_SET", "contextEntityType", "CUSTOMER"));
        List<IntentEvaluationReporter.EvaluationObservation> observations = new IntentUnderstandingEvaluationRunner().evaluate(
            List.of(Map.of("id", "reference", "turns", List.of(turn))), (message, slots, handles) -> {
                assertEquals(1, handles.size());
                ConversationUnderstandingResult result = new ConversationUnderstandingResult();
                result.setInteractionMode(BusinessInteractionMode.FOLLOW_UP);
                SemanticScope scope = new SemanticScope();
                scope.setRequiredKind(ContextHandleKind.ENTITY_SET); scope.setRequiredEntityType(SemanticEntityType.CUSTOMER);
                SemanticRequestFrame frame = new SemanticRequestFrame(); frame.setScope(scope);
                result.setFrames(List.of(frame));
                return result;
            });
        assertEquals(1, observations.size());
        assertEquals(1D, new IntentEvaluationReporter().summarize(observations).get("referenceResolutionAccuracy"));
    }
}
