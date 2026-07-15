package me.zhengjie.modules.agent.security.impl;

import me.zhengjie.modules.agent.security.AgentAccessContext;
import me.zhengjie.modules.agent.security.AgentQueryPermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工具权限校验实现；入口权限和工具业务权限必须同时满足。
 */
@Service
public class DefaultAgentQueryPermissionService implements AgentQueryPermissionService {

    private static final String AGENT_ENTRY_PERMISSION = "agentDiagnosis:list";
    private static final String ADMIN_PERMISSION = "admin";

    /** {@inheritDoc} */
    @Override
    public void require(AgentAccessContext context, String... requiredPermissions) {
        if (isAdministrator(context)) {
            return;
        }
        if (context == null || context.getPermissions() == null || !context.getPermissions().contains(AGENT_ENTRY_PERMISSION)) {
            throw denied();
        }
        if (requiredPermissions != null && Arrays.stream(requiredPermissions)
                .anyMatch(permission -> permission != null && !context.getPermissions().contains(permission))) {
            throw denied();
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<String> availableToolNames(AgentAccessContext context) {
        if (isAdministrator(context)) {
            return List.of("resolveCustomer", "customerOverview", "listOrders", "orderDetail", "listMealPlans",
                "listVerifications", "listRefunds", "packageDetail", "listDishes", "listScheduledDishes", "previewDishCandidates", "explainRule",
                "getDailyCustomerWorkload", "getCustomerProfileCount", "getActiveCustomerSummary", "getExpiringOrderSummary", "getMealPlanFailureSummary");
        }
        if (context == null || context.getPermissions() == null || !context.getPermissions().contains(AGENT_ENTRY_PERMISSION)) {
            return List.of();
        }
        List<String> tools = new ArrayList<>();
        if (has(context, "customerProfile:list")) {
            tools.add("resolveCustomer");
        }
        if (has(context, "customerProfile:list") && has(context, "customerOrder:list")) {
            tools.add("customerOverview");
        }
        if (has(context, "customerOrder:list")) {
            tools.add("listOrders");
            tools.add("orderDetail");
        }
        if (has(context, "customerProfile:list")) {
            tools.add("getCustomerProfileCount");
        }
        if (has(context, "mealPlan:list")) {
            tools.add("listMealPlans");
            tools.add("listVerifications");
        }
        if (has(context, "customerOrder:list") && has(context, "mealPlan:list")) {
            tools.add("listRefunds");
        }
        if (has(context, "package:list")) {
            tools.add("packageDetail");
        }
        if (has(context, "dish:list")) {
            tools.add("listDishes");
        }
        if (has(context, "mealPlan:list") && has(context, "dish:list")) {
            tools.add("listScheduledDishes");
        }
        if (has(context, "mealPlan:list")) {
            tools.add("getDailyCustomerWorkload");
            tools.add("getMealPlanFailureSummary");
        }
        if (has(context, "customerOrder:list")) {
            tools.add("getActiveCustomerSummary");
            tools.add("getExpiringOrderSummary");
        }
        if (has(context, "customerProfile:list") && has(context, "customerOrder:list") && has(context, "package:list") && has(context, "dish:list")) {
            tools.add("previewDishCandidates");
        }
        tools.add("explainRule");
        return tools;
    }

    /** 判断上下文是否具备单项业务权限。 */
    private boolean has(AgentAccessContext context, String permission) {
        return context.getPermissions().contains(permission);
    }

    /**
     * 判断是否为系统管理员。
     *
     * 管理员登录态只会携带 {@code admin} 权限，不会展开角色菜单权限；因此 Agent 内部只读查询
     * 需要在这里显式识别管理员，避免被工具白名单预过滤或内部接口鉴权拒绝。
     *
     * @param context 已签名的客服访问上下文
     * @return 当前上下文包含管理员权限时返回 {@code true}
     */
    private boolean isAdministrator(AgentAccessContext context) {
        return context != null && context.getPermissions() != null && context.getPermissions().contains(ADMIN_PERMISSION);
    }

    private ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent business query permission denied");
    }
}
