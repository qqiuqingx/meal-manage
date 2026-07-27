package me.zhengjie.agent.tool;

/** 工具唯一元数据源，包含授权、返回预算与超时限制。 */
public record ToolDescriptor(String name, String requiredPermission, int maxResults, int timeoutMillis,
                             String sensitivity, String inputSchema, String outputSchema) { }
