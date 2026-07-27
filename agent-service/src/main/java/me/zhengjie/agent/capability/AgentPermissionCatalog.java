package me.zhengjie.agent.capability;

import me.zhengjie.agent.tool.ToolCatalog;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Agent 受控能力可声明的权限目录。
 *
 * <p>能力 YAML 只能引用此处或 ToolCatalog 已登记的权限，避免拼写错误使能力在运行期静默失效。</p>
 */
public final class AgentPermissionCatalog {
    private static final Set<String> KNOWN = knownPermissions();
    private AgentPermissionCatalog() { }
    public static boolean contains(String permission) { return permission != null && KNOWN.contains(permission); }
    public static Set<String> all() { return KNOWN; }
    private static Set<String> knownPermissions() {
        Set<String> permissions = new LinkedHashSet<>();
        ToolCatalog.descriptors().forEach(item -> {
            for (String value : item.requiredPermission().split("\\+")) permissions.add(value);
        });
        // 主系统以更细粒度接口权限执行二次校验；它们同样是能力目录的受控声明值。
        permissions.add("mealVerification:list");
        permissions.add("mealRefund:list");
        return Set.copyOf(permissions);
    }
}
