package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

/**
 * 无状态会话存储适配器。
 *
 * <p>每次读取都从主系统信封随后注入的可信快照开始，保存仅更新本轮对象而不写进 JVM 缓存，
 * 因而多实例和重启不会把本地状态误认为真相源。</p>
 */
@Component
@ConditionalOnProperty(name = "agent.chat.stateful-session-cache-enabled", havingValue = "false", matchIfMissing = true)
public class RequestSnapshotMealPlanChatSessionStore implements MealPlanChatSessionStore {
    @Override
    public MealPlanChatSession getOrCreate(String sessionId) { return newSession(resolveSessionId(sessionId)); }
    @Override
    public void save(MealPlanChatSession session) { if (session != null) session.setUpdatedAt(Instant.now()); }
    @Override
    public MealPlanChatSession reset(String sessionId) { return newSession(resolveSessionId(sessionId)); }
    private MealPlanChatSession newSession(String sessionId) {
        MealPlanChatSession session = new MealPlanChatSession();
        session.setSessionId(sessionId); session.setSlots(new DiagnosisSlots());
        session.setConversationState(DiagnosisConversationState.initialize()); session.setUpdatedAt(Instant.now());
        return session;
    }
    private String resolveSessionId(String sessionId) { return sessionId == null || sessionId.trim().isEmpty() ? UUID.randomUUID().toString() : sessionId.trim(); }
}
