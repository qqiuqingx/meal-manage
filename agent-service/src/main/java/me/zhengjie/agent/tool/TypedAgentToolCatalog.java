package me.zhengjie.agent.tool;

import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring 收集的强类型工具目录，供新能力注册工具使用。
 *
 * <p>历史 QueryPlan 工具仍通过 {@link ToolCatalog} 兼容执行；新工具无需扩展中心 Executor。</p>
 */
@Component
public class TypedAgentToolCatalog {
    private final Map<String, AgentTool<?, ?>> tools;

    public TypedAgentToolCatalog(List<AgentTool<?, ?>> tools) {
        Map<String, AgentTool<?, ?>> collected = new LinkedHashMap<>();
        for (AgentTool<?, ?> tool : tools) {
            if (tool == null || tool.descriptor() == null || blank(tool.descriptor().name())
                || blank(tool.descriptor().requiredPermission()) || tool.inputType() == null || tool.outputType() == null) {
                throw new IllegalStateException("Invalid typed AgentTool registration");
            }
            if (collected.putIfAbsent(tool.descriptor().name(), tool) != null) {
                throw new IllegalStateException("Duplicate AgentTool name: " + tool.descriptor().name());
            }
        }
        this.tools = Map.copyOf(collected);
    }

    /** 按主系统下发的工具白名单裁剪模型或能力可见的强类型工具。 */
    public Collection<AgentTool<?, ?>> visibleTo(Set<String> availableTools) {
        return tools.values().stream().filter(tool -> availableTools == null || availableTools.contains(tool.descriptor().name()))
            .collect(Collectors.toUnmodifiableList());
    }

    /** 获取唯一登记工具；未知名称不能回退为任意方法调用。 */
    public AgentTool<?, ?> require(String toolName) {
        AgentTool<?, ?> tool = tools.get(toolName);
        if (tool == null) throw new IllegalArgumentException("TOOL_NOT_AVAILABLE: " + toolName);
        return tool;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
