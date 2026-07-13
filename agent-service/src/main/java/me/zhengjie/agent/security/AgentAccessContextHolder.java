package me.zhengjie.agent.security;

/**
 * 保存当前 HTTP 请求的主系统签名访问上下文，仅用于向主系统内部工具原样透传。
 */
public final class AgentAccessContextHolder {

    private static final ThreadLocal<String> ACCESS_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<java.util.Set<String>> AVAILABLE_TOOLS = new ThreadLocal<>();

    private AgentAccessContextHolder() {
    }

    /** 绑定当前请求的访问上下文和会话标识。 */
    public static void bind(String accessContext, String sessionId) {
        if (accessContext != null && !accessContext.trim().isEmpty()) ACCESS_CONTEXT.set(accessContext.trim());
        if (sessionId != null && !sessionId.trim().isEmpty()) SESSION_ID.set(sessionId.trim());
    }

    /** 绑定由主系统计算的本轮工具白名单；空值表示兼容旧调用，不预过滤工具。 */
    public static void bindAvailableTools(java.util.List<String> availableTools) {
        if (availableTools != null) AVAILABLE_TOOLS.set(new java.util.LinkedHashSet<>(availableTools));
    }

    /** 返回当前请求的签名访问上下文。 */
    public static String accessContext() { return ACCESS_CONTEXT.get(); }

    /** 返回当前请求的会话 ID。 */
    public static String sessionId() { return SESSION_ID.get(); }

    /** 返回本轮工具白名单；null 表示调用方未提供白名单。 */
    public static java.util.Set<String> availableTools() { return AVAILABLE_TOOLS.get(); }

    /** 请求结束后清理线程变量，避免线程池串用。 */
    public static void clear() { ACCESS_CONTEXT.remove(); SESSION_ID.remove(); AVAILABLE_TOOLS.remove(); }
}
