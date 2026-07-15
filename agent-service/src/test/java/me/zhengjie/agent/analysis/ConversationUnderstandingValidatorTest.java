package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 回归会话理解协议的帧数和引用前置条件。 */
class ConversationUnderstandingValidatorTest {
    @Test
    void requiresResolvedContextReference() {
        SemanticRequestFrame frame = new SemanticRequestFrame(); frame.setFrameId("f1"); frame.setGoal(SemanticGoal.QUERY);
        frame.setTargetEntity(SemanticEntityType.CUSTOMER); frame.setOutputShape(SemanticOutputShape.DETAIL_LIST);
        SemanticScope scope = new SemanticScope(); scope.setType(SemanticScope.Type.CONTEXT_REFERENCE); frame.setScope(scope);
        ConversationUnderstandingResult result = new ConversationUnderstandingResult(); result.setFrames(List.of(frame));
        assertEquals("CONTEXT_REFERENCE_MISSING", new ConversationUnderstandingValidator().validate(result));
    }
}
