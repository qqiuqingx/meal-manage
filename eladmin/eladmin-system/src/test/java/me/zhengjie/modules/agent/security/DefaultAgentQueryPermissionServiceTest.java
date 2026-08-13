package me.zhengjie.modules.agent.security;

import me.zhengjie.modules.agent.security.impl.DefaultAgentQueryPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Agent 工具权限映射安全测试。 */
class DefaultAgentQueryPermissionServiceTest {

    private final DefaultAgentQueryPermissionService service = new DefaultAgentQueryPermissionService(true);

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

        assertTrue(service.availableToolNames(context).containsAll(Arrays.asList(
            "searchCustomerProfiles", "listMealPlans", "queryBusinessMetrics", "explainBusinessRule")));
        org.junit.jupiter.api.Assertions.assertFalse(service.availableToolNames(context).contains("listVerifications"));
        org.junit.jupiter.api.Assertions.assertFalse(service.availableToolNames(context).contains("listRefunds"));
    }

    /** 管理员登录态仅携带 admin 权限时，仍应具备全部 Agent 只读查询工具。 */
    @Test
    void shouldAllowAdministratorToUseAllBusinessQueryTools() {
        AgentAccessContext context = context("admin");

        assertDoesNotThrow(() -> service.require(context, "customerProfile:list", "customerOrder:list"));
        assertTrue(service.availableToolNames(context).size() == 13);
    }

    /** 进行中订单统计复用订单只读权限，不应额外要求客户档案或排餐权限。 */
    @Test
    void shouldExposeMetricsWithAnyMetricSourcePermission() {
        AgentAccessContext context = context("agentDiagnosis:list", "customerOrder:list");

        org.junit.jupiter.api.Assertions.assertTrue(
            service.availableToolNames(context).contains("queryBusinessMetrics"));
    }

    /** 只有核销权限的客服也必须能使用统一指标工具查询核销记录总数。 */
    @Test
    void shouldExposeMetricsWithVerificationPermission() {
        AgentAccessContext context = context("agentDiagnosis:list", "mealVerification:list");

        assertTrue(service.availableToolNames(context).contains("queryBusinessMetrics"));
        assertTrue(service.availableToolNames(context).contains("listVerifications"));
    }

    /** 菜单工具需要同时具备排餐与菜品权限，不能因单项权限越权。 */
    @Test
    void shouldNotExposeScheduledMenuWithoutDishPermission() {
        AgentAccessContext context = context("agentDiagnosis:list", "mealPlan:list");

        org.junit.jupiter.api.Assertions.assertFalse(service.availableToolNames(context).contains("listScheduledDishes"));
    }

    /** 新增客户和新增订单权限应分别暴露同一个受控草稿工具。 */
    @Test
    void shouldExposeFormDraftToolForEitherTargetAddPermission() {
        assertTrue(service.availableToolNames(context("agentDiagnosis:list", "customerProfile:add"))
            .contains("saveFormDraft"));
        assertTrue(service.availableToolNames(context("agentDiagnosis:list", "customerOrder:add"))
            .contains("saveFormDraft"));
    }

    /** 发布开关关闭时只保留原有只读工具，管理员也不能绕过该开关。 */
    @Test
    void shouldHideFormDraftToolWhenFeatureDisabled() {
        DefaultAgentQueryPermissionService disabled = new DefaultAgentQueryPermissionService(false);

        org.junit.jupiter.api.Assertions.assertFalse(disabled.availableToolNames(context("admin"))
            .contains("saveFormDraft"));
        org.junit.jupiter.api.Assertions.assertFalse(disabled.availableToolNames(
            context("agentDiagnosis:list", "customerProfile:add", "customerOrder:add"))
            .contains("saveFormDraft"));
    }

    private AgentAccessContext context(String... permissions) {
        AgentAccessContext context = new AgentAccessContext();
        context.setPermissions(Arrays.asList(permissions));
        return context;
    }
}
