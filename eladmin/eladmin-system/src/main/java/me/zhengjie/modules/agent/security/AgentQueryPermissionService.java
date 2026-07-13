package me.zhengjie.modules.agent.security;

/**
 * Agent 工具与客服业务权限的映射校验服务。
 */
public interface AgentQueryPermissionService {

    /**
     * 验证上下文同时具备 Agent 入口权限和指定业务工具权限。
     *
     * @param context 已校验访问上下文
     * @param requiredPermissions 工具所需业务权限
     */
    void require(AgentAccessContext context, String... requiredPermissions);

    /**
     * 根据当前客服的入口和业务权限计算本轮可见的 Agent 工具。
     *
     * @param context 已校验访问上下文
     * @return 可安全向 Agent 暴露的固定工具名称，不包含完整权限集合
     */
    java.util.List<String> availableToolNames(AgentAccessContext context);
}
