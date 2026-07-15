package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.ContextHandleKind;
import me.zhengjie.agent.analysis.domain.ConversationContextHandle;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.analysis.domain.SemanticScope;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 根据类型、时效和显著度解析语义帧所需的上下文，不读取用户表面词决定引用对象。 */
public class ContextReferenceResolver {
    private static final double AMBIGUOUS_DELTA = 0.10D;

    /** 为 CONTEXT_REFERENCE 范围绑定唯一的有效上下文句柄。 */
    public Resolution resolve(SemanticRequestFrame frame, List<ConversationContextHandle> handles, OffsetDateTime now) {
        if (frame == null || frame.getScope() == null || frame.getScope().getType() != SemanticScope.Type.CONTEXT_REFERENCE) return Resolution.notRequired();
        SemanticScope scope = frame.getScope();
        List<Candidate> candidates = (handles == null ? List.<ConversationContextHandle>of() : handles).stream()
            .filter(handle -> compatible(scope, handle, now)).map(handle -> new Candidate(handle, score(scope, handle, now)))
            .sorted(Comparator.comparingDouble(Candidate::score).reversed()).toList();
        if (candidates.isEmpty()) return Resolution.missing();
        if (candidates.size() > 1 && candidates.get(0).score() - candidates.get(1).score() < AMBIGUOUS_DELTA) return Resolution.ambiguous();
        scope.setResolvedHandleId(candidates.get(0).handle().getHandleId());
        return Resolution.resolved(candidates.get(0).handle());
    }

    private boolean compatible(SemanticScope scope, ConversationContextHandle handle, OffsetDateTime now) {
        return handle != null && handle.getHandleId() != null && (handle.getExpiresAt() == null || !handle.getExpiresAt().isBefore(now))
            && (scope.getRequiredKind() == null || scope.getRequiredKind() == handle.getKind())
            && (scope.getRequiredEntityType() == null || scope.getRequiredEntityType() == handle.getEntityType());
    }
    private double score(SemanticScope scope, ConversationContextHandle handle, OffsetDateTime now) {
        double score = handle.getSalience();
        if (scope.getRequiredKind() == handle.getKind()) score += .50D;
        if (scope.getRequiredEntityType() == handle.getEntityType()) score += .30D;
        if (handle.getCreatedAt() != null) score += Math.max(0D, .20D - Math.min(.20D, (now.toEpochSecond() - handle.getCreatedAt().toEpochSecond()) / 3600D / 100D));
        return score;
    }
    private record Candidate(ConversationContextHandle handle, double score) { }
    /** 受控引用解析结果，不携带候选业务数据。 */
    public record Resolution(Status status, ConversationContextHandle handle) {
        public static Resolution notRequired() { return new Resolution(Status.NOT_REQUIRED, null); }
        public static Resolution missing() { return new Resolution(Status.MISSING, null); }
        public static Resolution ambiguous() { return new Resolution(Status.AMBIGUOUS, null); }
        public static Resolution resolved(ConversationContextHandle handle) { return new Resolution(Status.RESOLVED, handle); }
    }
    public enum Status { NOT_REQUIRED, RESOLVED, MISSING, AMBIGUOUS }
}
