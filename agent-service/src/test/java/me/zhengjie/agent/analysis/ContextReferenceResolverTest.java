package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.ContextHandleKind;
import me.zhengjie.agent.analysis.domain.ConversationContextHandle;
import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.analysis.domain.SemanticScope;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证上下文引用依据句柄类型和显著度解析，不依赖用户原文词典。 */
class ContextReferenceResolverTest {
    @Test
    void resolvesCompatibleCustomerSet() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        ConversationContextHandle handle = new ConversationContextHandle();
        handle.setHandleId("ctx-active"); handle.setKind(ContextHandleKind.ENTITY_SET); handle.setEntityType(SemanticEntityType.CUSTOMER);
        handle.setSalience(1D); handle.setCreatedAt(now); handle.setExpiresAt(now.plusMinutes(30));
        SemanticRequestFrame frame = new SemanticRequestFrame();
        SemanticScope scope = new SemanticScope(); scope.setType(SemanticScope.Type.CONTEXT_REFERENCE);
        scope.setRequiredKind(ContextHandleKind.ENTITY_SET); scope.setRequiredEntityType(SemanticEntityType.CUSTOMER); frame.setScope(scope);
        ContextReferenceResolver.Resolution result = new ContextReferenceResolver().resolve(frame, List.of(handle), now);
        assertEquals(ContextReferenceResolver.Status.RESOLVED, result.status());
        assertEquals("ctx-active", frame.getScope().getResolvedHandleId());
    }

    @Test
    void rejectsIncompatibleHandle() {
        ConversationContextHandle handle = new ConversationContextHandle();
        handle.setHandleId("ctx-order"); handle.setKind(ContextHandleKind.ENTITY); handle.setEntityType(SemanticEntityType.ORDER);
        SemanticRequestFrame frame = new SemanticRequestFrame();
        SemanticScope scope = new SemanticScope(); scope.setType(SemanticScope.Type.CONTEXT_REFERENCE);
        scope.setRequiredKind(ContextHandleKind.ENTITY_SET); scope.setRequiredEntityType(SemanticEntityType.CUSTOMER); frame.setScope(scope);
        assertEquals(ContextReferenceResolver.Status.MISSING,
            new ContextReferenceResolver().resolve(frame, List.of(handle), OffsetDateTime.now(ZoneOffset.ofHours(8))).status());
    }
}
