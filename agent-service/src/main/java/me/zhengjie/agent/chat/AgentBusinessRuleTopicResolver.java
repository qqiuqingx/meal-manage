package me.zhengjie.agent.chat;

import java.util.Optional;

/** 将用户规则问法收敛为已登记的受控主题，禁止将原文作为规则标识传给主系统。 */
public interface AgentBusinessRuleTopicResolver {

    /**
     * 解析当前消息匹配的规则主题。
     *
     * @param message 用户原始问题
     * @return 已登记规则主题；未命中时为空
     */
    Optional<String> resolve(String message);
}
