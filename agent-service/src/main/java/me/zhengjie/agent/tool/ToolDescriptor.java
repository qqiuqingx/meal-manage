package me.zhengjie.agent.tool;

/** 工具唯一元数据源，包含授权、读写属性、版本化 Schema、返回预算与超时限制。 */
public record ToolDescriptor(String name, String domain, String action, String requiredPermission,
                             int maxResults, int timeoutMillis, String dataClassification,
                             boolean readOnly, String inputSchemaVersion, String outputSchemaVersion,
                             String inputSchema, String outputSchema) {

    /** 兼容迁移期工具定义的构造方式，默认按内部只读、v1 Schema 登记。 */
    public ToolDescriptor(String name, String requiredPermission, int maxResults, int timeoutMillis,
                          String dataClassification, String inputSchema, String outputSchema) {
        this(name, "UNSPECIFIED", "QUERY", requiredPermission, maxResults, timeoutMillis,
            dataClassification, true, "v1", "v1", inputSchema, outputSchema);
    }
}
