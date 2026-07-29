package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
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
public class RequestSnapshotMealPlanChatSessionStore implements MealPlanChatSessionStore {

    /**
     * 为当前请求创建空白会话对象，后续由请求信封中的可信快照覆盖。
     *
     * @param sessionId 会话标识，可为空
     * @return 当前请求独享的会话对象
     */
    @Override
    public MealPlanChatSession getOrCreate(String sessionId) {
        return newSession(resolveSessionId(sessionId));
    }

    /**
     * 标记当前请求会话已更新，不向 JVM 共享缓存写入任何状态。
     *
     * @param session 当前请求会话
     */
    @Override
    public void save(MealPlanChatSession session) {
        if (session != null) {
            session.setUpdatedAt(Instant.now());
        }
    }

    /**
     * 为重置请求创建初始化会话对象。
     *
     * @param sessionId 会话标识，可为空
     * @return 初始化后的当前请求会话
     */
    @Override
    public MealPlanChatSession reset(String sessionId) {
        return newSession(resolveSessionId(sessionId));
    }

    /**
     * 构造当前请求独享的初始化会话。
     *
     * @param sessionId 已解析的会话标识
     * @return 初始化会话
     */
    private MealPlanChatSession newSession(String sessionId) {
        MealPlanChatSession session = new MealPlanChatSession();
        session.setSessionId(sessionId);
        session.setSlots(new DiagnosisSlots());
        session.setConversationState(DiagnosisConversationState.initialize());
        session.setUpdatedAt(Instant.now());
        return session;
    }

    /**
     * 规范化会话标识，空标识使用随机值隔离请求。
     *
     * @param sessionId 原始会话标识
     * @return 可用于当前请求的会话标识
     */
    private String resolveSessionId(String sessionId) {
        return sessionId == null || sessionId.trim().isEmpty()
            ? UUID.randomUUID().toString()
            : sessionId.trim();
    }
}
