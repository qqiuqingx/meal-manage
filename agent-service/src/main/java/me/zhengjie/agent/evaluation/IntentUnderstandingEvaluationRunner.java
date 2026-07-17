package me.zhengjie.agent.evaluation;

import me.zhengjie.agent.analysis.ConversationUnderstandingService;
import me.zhengjie.agent.analysis.domain.ContextHandleKind;
import me.zhengjie.agent.analysis.domain.ConversationContextHandle;
import me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult;
import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.analysis.domain.SemanticScope;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 执行脱敏的会话理解评测案例。
 *
 * <p>评测器只向模型提供合成的上下文句柄，不提供真实客户、订单或工具响应；输出由
 * {@link IntentEvaluationReporter} 汇总，不能包含用户原文。</p>
 */
public class IntentUnderstandingEvaluationRunner {

    /**
     * 顺序执行案例中的多轮输入并返回逐轮评测观察结果。
     *
     * @param cases 脱敏的评测案例
     * @param understandingService 被测会话理解服务
     * @return 不携带原文的评测观察结果
     */
    @SuppressWarnings("unchecked")
    public List<IntentEvaluationReporter.EvaluationObservation> evaluate(List<Map<String, Object>> cases,
                                                                           ConversationUnderstandingService understandingService) {
        List<IntentEvaluationReporter.EvaluationObservation> observations = new ArrayList<>();
        if (cases == null || understandingService == null) {
            return observations;
        }
        for (Map<String, Object> item : cases) {
            List<ConversationContextHandle> handles = new ArrayList<>();
            Object rawTurns = item.get("turns");
            if (!(rawTurns instanceof List<?>)) {
                continue;
            }
            for (Object rawTurn : (List<?>) rawTurns) {
                if (!(rawTurn instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> turn = (Map<String, Object>) rawTurn;
                Map<String, Object> expected = turn.get("expected") instanceof Map<?, ?>
                    ? (Map<String, Object>) turn.get("expected") : Map.of();
                addSyntheticReferenceHandle(expected, handles);
                long startedAt = System.nanoTime();
                ConversationUnderstandingResult actual = understandingService.understand(
                    String.valueOf(turn.getOrDefault("user", "")), new DiagnosisSlots(), handles);
                long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
                observations.add(observe(expected, actual, latencyMs));
            }
        }
        return observations;
    }

    /** 为声明可解析引用的脱敏案例提供最小、可验证的合成句柄。 */
    private void addSyntheticReferenceHandle(Map<String, Object> expected, List<ConversationContextHandle> handles) {
        if (expected.containsKey("clarificationCode") || !expected.containsKey("contextHandleKind")) {
            return;
        }
        String kind = String.valueOf(expected.get("contextHandleKind"));
        if (handles.stream().anyMatch(handle -> handle.getKind() != null && kind.equals(handle.getKind().name()))) {
            return;
        }
        ConversationContextHandle handle = new ConversationContextHandle();
        handle.setHandleId("eval-context-" + (handles.size() + 1));
        handle.setKind(ContextHandleKind.valueOf(kind));
        handle.setEntityType(SemanticEntityType.valueOf(String.valueOf(expected.get("contextEntityType"))));
        handle.setDefinitionId("AGENT_ACTIVE_CUSTOMER_V1");
        handle.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        handles.add(handle);
    }

    /** 将模型输出与受控期望比较；未声明的维度不参与本条断言。 */
    private IntentEvaluationReporter.EvaluationObservation observe(Map<String, Object> expected,
                                                                     ConversationUnderstandingResult actual,
                                                                     long latencyMs) {
        SemanticRequestFrame frame = actual == null || actual.getFrames() == null || actual.getFrames().isEmpty()
            ? null : actual.getFrames().get(0);
        boolean interactionCorrect = matches(expected.get("interactionMode"), actual == null ? null : actual.getInteractionMode());
        boolean expectedReference = expected.containsKey("contextHandleKind");
        SemanticScope scope = frame == null ? null : frame.getScope();
        boolean referenceCorrect = !expectedReference || (scope != null
            && matches(expected.get("contextHandleKind"), scope.getRequiredKind())
            && matches(expected.get("contextEntityType"), scope.getRequiredEntityType()));
        boolean expectedClarification = Boolean.TRUE.equals(expected.get("requiresClarification")) || expected.containsKey("clarificationCode");
        boolean clarificationCorrect = actual != null && actual.isRequiresClarification() == expectedClarification
            && (!expected.containsKey("clarificationCode") || matches(expected.get("clarificationCode"), actual.getClarificationCode()));
        boolean targetCorrect = !expected.containsKey("targetEntity") || (frame != null && matches(expected.get("targetEntity"), frame.getTargetEntity()));
        boolean frameCountCorrect = !expected.containsKey("frameCount") || (actual != null && actual.getFrames().size() == integer(expected.get("frameCount")));
        boolean operationsCorrect = !expected.containsKey("operations") || (frame != null && containsAll(frame.getOperations(), expected.get("operations")));
        boolean outputCorrect = !expected.containsKey("outputShape") || (frame != null && matches(expected.get("outputShape"), frame.getOutputShape()));
        boolean slotCorrect = true;
        boolean unknownCorrect = !expectedClarification || clarificationCorrect;
        boolean overallCorrect = interactionCorrect && referenceCorrect && clarificationCorrect && targetCorrect
            && frameCountCorrect && operationsCorrect && outputCorrect;
        String confidenceBucket = actual == null || actual.getConfidenceBucket() == null ? "UNKNOWN" : actual.getConfidenceBucket();
        return new IntentEvaluationReporter.EvaluationObservation(interactionCorrect, referenceCorrect, clarificationCorrect,
            targetCorrect, frameCountCorrect && operationsCorrect && outputCorrect, slotCorrect, unknownCorrect,
            confidenceBucket, overallCorrect, latencyMs);
    }

    /** 将枚举或字符串统一为名称比较，避免评测逻辑依赖实现类。 */
    private boolean matches(Object expected, Object actual) {
        return expected == null || (actual != null && String.valueOf(expected).equals(String.valueOf(actual)));
    }

    /** 判断模型返回的受控枚举包含案例声明的全部操作。 */
    private boolean containsAll(List<?> actual, Object expected) {
        if (!(expected instanceof List<?>)) {
            return false;
        }
        return ((List<?>) expected).stream().allMatch(item -> actual != null
            && actual.stream().anyMatch(value -> String.valueOf(item).equals(String.valueOf(value))));
    }

    /** 将 YAML 数字安全转换为帧数量。 */
    private int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }
}
