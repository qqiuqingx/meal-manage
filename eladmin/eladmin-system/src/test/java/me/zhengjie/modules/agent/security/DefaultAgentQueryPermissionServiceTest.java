package me.zhengjie.modules.agent.security;

import me.zhengjie.modules.agent.security.impl.DefaultAgentQueryPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Agent 工具权限映射安全测试。 */
class DefaultAgentQueryPermissionServiceTest {

    private final DefaultAgentQueryPermissionService service = new DefaultAgentQueryPermissionService();

    /** 入口权限不能替代业务权限。 */
    @Test
    void shouldRejectAgentOnlyPermissionWithoutBusinessPermission() {
        AgentAccessContext context = context("agentDiagnosis:list");
        assertThrows(ResponseStatusException.class, () -> service.require(context, "customerOrder:list"));
    }

    /** 同时满足入口和业务权限才能调用工具。 */
    @Test
    void shouldAllowWhenEntryAndBusinessPermissionsPresent() {
        AgentAccessContext context = context("agentDiagnosis:list", "customerOrder:list");
        assertDoesNotThrow(() -> service.require(context, "customerOrder:list"));
    }

    /** Agent 仅能获知当前客服实际可执行的工具，而不是完整权限集合。 */
    @Test
    void shouldExposeOnlyToolsAllowedByBusinessPermissions() {
        AgentAccessContext context = context("agentDiagnosis:list", "customerProfile:list", "mealPlan:list");

        assertEquals(Arrays.asList("resolveCustomer", "getCustomerProfileCount", "listMealPlans", "listVerifications",
                "getDailyCustomerWorkload", "getMealPlanFailureSummary", "explainRule"),
            service.availableToolNames(context));
    }

    /** 管理员登录态仅携带 admin 权限时，仍应具备全部 Agent 只读查询工具。 */
    @Test
    void shouldAllowAdministratorToUseAllBusinessQueryTools() {
        AgentAccessContext context = context("admin");

        assertDoesNotThrow(() -> service.require(context, "customerProfile:list", "customerOrder:list"));
        assertEquals(Arrays.asList("resolveCustomer", "customerOverview", "listOrders", "orderDetail", "listMealPlans",
                "listVerifications", "listRefunds", "packageDetail", "listDishes", "listScheduledDishes", "previewDishCandidates", "explainRule",
                "getDailyCustomerWorkload", "getCustomerProfileCount", "getActiveCustomerSummary", "getExpiringOrderSummary", "getMealPlanFailureSummary"),
            service.availableToolNames(context));
    }

    /** 菜单工具需要同时具备排餐与菜品权限，不能因单项权限越权。 */
    @Test
    void shouldNotExposeScheduledMenuWithoutDishPermission() {
        AgentAccessContext context = context("agentDiagnosis:list", "mealPlan:list");

        org.junit.jupiter.api.Assertions.assertFalse(service.availableToolNames(context).contains("listScheduledDishes"));
    }

    private AgentAccessContext context(String... permissions) {
        AgentAccessContext context = new AgentAccessContext();
        context.setPermissions(Arrays.asList(permissions));
        return context;
    }
}
