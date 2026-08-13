package me.zhengjie.modules.agent.formdraft.service;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeResolver;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 为聊天链路读取当前客服可修订的可信表单草稿上下文。 */
@Component
@RequiredArgsConstructor
public class AgentFormDraftConversationContextResolver {
    private final AgentFormDraftService draftService;
    private final AgentCustomerDataScopeResolver dataScopeResolver;

    /**
     * 在当前登录客服数据范围内读取指定或最近活动草稿。
     *
     * @param draftId 固定动作指定的草稿 ID，可空
     * @param sessionId 当前聊天会话 ID
     * @return 可安全下发给 Agent 的类型化草稿上下文
     */
    public Map<String, Object> resolve(String draftId, String sessionId) {
        try {
            AgentCustomerDataScopeContext.bind(dataScopeResolver.resolveCurrent());
            return draftService.conversationContext(draftId, sessionId, SecurityUtils.getCurrentUserId());
        } finally {
            AgentCustomerDataScopeContext.clear();
        }
    }
}
