package me.zhengjie.modules.agent.security.impl;

import me.zhengjie.modules.agent.security.AgentAccessContext;
import me.zhengjie.modules.agent.security.AgentQueryPermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Agent 工具权限校验实现。
 *
 * <p>入口权限、业务权限和工具白名单分别校验；模型只会收到当前上下文实际允许的
 * 12 个统一只读工具名称，不保留旧工具别名或旧权限组合兼容分支。</p>
 */
@Service
public class DefaultAgentQueryPermissionService implements AgentQueryPermissionService {

    private static final String AGENT_ENTRY_PERMISSION = "agentDiagnosis:list";
    private static final String ADMIN_PERMISSION = "admin";
    private static final List<ToolDefinition> TOOL_DEFINITIONS = Collections.unmodifiableList(Arrays.asList(
        new ToolDefinition("searchCustomerProfiles", "customerProfile:list"),
        new ToolDefinition("searchServiceCustomers", "customerOrder:list"),
        new ToolDefinition("getServiceCustomerDetail", "customerProfile:list", "customerOrder:list"),
        new ToolDefinition("listMealPlans", "mealPlan:list"),
        new ToolDefinition("listVerifications", "mealVerification:list"),
        new ToolDefinition("listRefunds", "mealRefund:list"),
        new ToolDefinition("previewDishCandidates", "customerProfile:list", "customerOrder:list", "package:list", "dish:list"),
        new ToolDefinition("listScheduledDishes", "mealPlan:list", "dish:list"),
        new ToolDefinition("searchDishes", "dish:list"),
        new ToolDefinition("getPackageDetail", "package:list"),
        new ToolDefinition("queryBusinessMetrics"),
        new ToolDefinition("explainBusinessRule", AGENT_ENTRY_PERMISSION)
    ));

    /** {@inheritDoc} */
    @Override
    public void require(AgentAccessContext context, String... requiredPermissions) {
        if (isAdministrator(context)) {
            return;
        }
        if (!has(context, AGENT_ENTRY_PERMISSION)) {
            throw denied();
        }
        if (requiredPermissions != null) {
            for (String permission : requiredPermissions) {
                if (permission != null && !has(context, permission)) {
                    throw denied();
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<String> availableToolNames(AgentAccessContext context) {
        if (context == null || !isAdministrator(context) && !has(context, AGENT_ENTRY_PERMISSION)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (ToolDefinition definition : TOOL_DEFINITIONS) {
            if (isAdministrator(context) || canUse(context, definition)) {
                result.add(definition.name);
            }
        }
        return result;
    }

    /** 判断一个统一工具的全部业务权限是否满足。 */
    private boolean canUse(AgentAccessContext context, ToolDefinition definition) {
        if ("queryBusinessMetrics".equals(definition.name)) {
            return has(context, "customerProfile:list")
                || has(context, "customerOrder:list")
                || has(context, "mealPlan:list");
        }
        for (String permission : definition.permissions) {
            if (!has(context, permission)) {
                return false;
            }
        }
        return true;
    }

    /** 判断权限集合是否包含指定权限；权限别名不参与授权。 */
    private boolean has(AgentAccessContext context, String permission) {
        return context != null && context.getPermissions() != null
            && context.getPermissions().contains(permission);
    }

    /** 判断是否为系统管理员。 */
    private boolean isAdministrator(AgentAccessContext context) {
        return context != null && context.getPermissions() != null
            && context.getPermissions().contains(ADMIN_PERMISSION);
    }

    /** 生成不暴露内部权限细节的拒绝异常。 */
    private ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent business query permission denied");
    }

    /** 工具名和所需业务权限的不可变登记项。 */
    private static final class ToolDefinition {
        private final String name;
        private final List<String> permissions;

        /** 创建工具与主系统业务权限的固定映射定义。 */
        private ToolDefinition(String name, String... permissions) {
            this.name = name;
            this.permissions = Arrays.asList(permissions);
        }
    }
}
