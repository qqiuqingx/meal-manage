package me.zhengjie.agent.query.tool;

import me.zhengjie.agent.tool.ToolCatalog;
import java.util.Collection;

/** @deprecated 仅为历史调用保留；工具定义请使用 {@link ToolCatalog}。 */
@Deprecated
public final class AgentBusinessToolRegistry {
    private AgentBusinessToolRegistry() { }
    public static boolean isRegistered(String name) { return ToolCatalog.isRegistered(name); }
    public static Collection<AgentBusinessToolDescriptor> descriptors() { return ToolCatalog.descriptors(); }
    public static AgentBusinessToolDescriptor descriptor(String name) { return ToolCatalog.descriptor(name); }
    public static boolean isAvailable(String name, java.util.Set<String> availableTools) {
        return isRegistered(name) && (availableTools == null || availableTools.contains(name));
    }
}
