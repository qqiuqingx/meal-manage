package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 仅供多轮会话单元测试使用的内存状态夹具。
 *
 * <p>生产环境始终以主系统请求信封中的会话快照为真相源，不注册此实现。</p>
 */
final class InMemoryMealPlanChatSessionStore implements MealPlanChatSessionStore {

    private final ConcurrentMap<String, MealPlanChatSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    /**
     * 使用指定时钟和过期时间创建测试会话存储。
     *
     * @param clock 测试时钟
     * @param ttl 会话过期时间
     */
    InMemoryMealPlanChatSessionStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    /**
     * 获取未过期会话；不存在或已过期时创建新会话。
     *
     * @param sessionId 会话标识，可为空
     * @return 测试会话
     */
    @Override
    public MealPlanChatSession getOrCreate(String sessionId) {
        String resolvedSessionId = isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId.trim();
        MealPlanChatSession existing = sessions.get(resolvedSessionId);
        if (existing != null && !expired(existing)) {
            return existing;
        }
        MealPlanChatSession created = newSession(resolvedSessionId);
        sessions.put(resolvedSessionId, created);
        return created;
    }

    /**
     * 保存本轮测试会话并刷新更新时间。
     *
     * @param session 待保存会话
     */
    @Override
    public void save(MealPlanChatSession session) {
        session.setUpdatedAt(clock.instant());
        sessions.put(session.getSessionId(), session);
    }

    /**
     * 清空指定测试会话并返回初始化状态。
     *
     * @param sessionId 会话标识，可为空
     * @return 重置后的测试会话
     */
    @Override
    public MealPlanChatSession reset(String sessionId) {
        String resolvedSessionId = isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId.trim();
        MealPlanChatSession session = newSession(resolvedSessionId);
        sessions.put(resolvedSessionId, session);
        return session;
    }

    /**
     * 构造初始化测试会话。
     *
     * @param sessionId 已解析的会话标识
     * @return 初始化会话
     */
    private MealPlanChatSession newSession(String sessionId) {
        MealPlanChatSession session = new MealPlanChatSession();
        session.setSessionId(sessionId);
        session.setSlots(new DiagnosisSlots());
        session.setConversationState(DiagnosisConversationState.initialize());
        session.setUpdatedAt(clock.instant());
        return session;
    }

    /**
     * 判断测试会话是否超过过期时间。
     *
     * @param session 测试会话
     * @return 已过期返回 true
     */
    private boolean expired(MealPlanChatSession session) {
        Instant updatedAt = session.getUpdatedAt();
        return updatedAt == null || updatedAt.plus(ttl).isBefore(clock.instant());
    }

    /**
     * 判断文本是否为空。
     *
     * @param value 待判断文本
     * @return 空值或空白文本返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
