package me.zhengjie.agent.query.tool;

import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;

/** 已登记业务只读工具的受控元数据。 */
public record AgentBusinessToolDescriptor(String name, AgentQueryDomain domain, AgentQueryAction action,
                                          String requiredPermission, int maxResults, String sensitivity,
                                          String inputSchema, String outputSchema, int timeoutMillis) {
    /**
     * 兼容已有登记调用的简化构造器，并提供统一的内部只读协议默认值。
     */
    public AgentBusinessToolDescriptor(String name, AgentQueryDomain domain, AgentQueryAction action,
                                       String requiredPermission, int maxResults, String sensitivity) {
        this(name, domain, action, requiredPermission, maxResults, sensitivity,
            "QUERY_PLAN_ONLY", "SANITIZED_DTO", 3000);
    }
}
