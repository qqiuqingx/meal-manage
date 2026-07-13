package me.zhengjie.modules.agent.security;

/**
 * Agent 客服访问上下文的签发和校验服务。
 */
public interface AgentAccessContextService {

    /**
     * 根据当前已登录客服签发短期、不可篡改的上下文。
     *
     * @param sessionId 当前会话 ID
     * @param requestId 当前请求 ID
     * @return 可由 Agent 原样透传的签名上下文
     */
    String issue(String sessionId, String requestId);

    /**
     * 校验签名、有效期、请求链路和会话归属。
     *
     * @param token 已签发访问上下文
     * @param sessionId 当前会话 ID
     * @param requestId 当前请求 ID
     * @return 经校验的访问上下文
     */
    AgentAccessContext verify(String token, String sessionId, String requestId);
}
