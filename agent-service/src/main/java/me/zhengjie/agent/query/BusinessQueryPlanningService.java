package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.MealScope;

import java.util.List;

/** 将受控问题分析结果转换为 QueryPlan 2.0，不信任分析结果中的任意工具名。 */
public class BusinessQueryPlanningService {
    /**
     * 创建 QueryPlan；关键歧义或缺少领域时返回 null，调用方应展示澄清问题而非执行查询。
     *
     * @param analysis 已验证的规则或模型分析结果
     * @return 仅包含登记指标与工具的查询计划，不能执行时返回 null
     */
    public AgentQueryPlan plan(BusinessQuestionAnalysis analysis) {
        if (analysis == null || analysis.isRequiresClarification() || analysis.getDomains() == null || analysis.getDomains().isEmpty()) return null;
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setEntities(analysis.getEntities());
        plan.setFilters(analysis.getFilters());
        plan.setMetrics(analysis.getMetrics());
        plan.setDimensions(analysis.getDimensions());
        plan.setAnalysisSource(analysis.getSource());
        plan.setAnalysisConfidence(analysis.getConfidence());
        AgentQueryDomain domain = analysis.getDomains().get(0);
        plan.setDomain(domain);
        if (analysis.getQueryTarget() == BusinessQueryTarget.MEAL_PLAN_ALLERGY_ANALYSIS) return mealPlanAllergyPlan(plan, analysis);
        if (analysis.getQueryTarget() == BusinessQueryTarget.SCHEDULED_MENU) return scheduledMenuPlan(plan, analysis);
        if (domain == AgentQueryDomain.OPERATION_STATISTICS || domain == AgentQueryDomain.NATURAL_LANGUAGE_REPORT) return aggregationPlan(plan, analysis);
        if (domain == AgentQueryDomain.CUSTOMER) {
            boolean hasCustomer = analysis.getEntities() != null && (analysis.getEntities().getCustomerId() != null
                || notBlank(analysis.getEntities().getCustomerCode()) || notBlank(analysis.getEntities().getCustomerName()));
            plan.setAction(hasCustomer ? AgentQueryAction.OVERVIEW : AgentQueryAction.LIST);
            plan.setToolNames(hasCustomer ? List.of("customerOverview") : List.of("resolveCustomer"));
            return plan;
        }
        if (domain == AgentQueryDomain.ORDER) {
            boolean hasOrder = analysis.getEntities() != null && (analysis.getEntities().getOrderId() != null || notBlank(analysis.getEntities().getOrderCode()));
            plan.setAction(hasOrder ? AgentQueryAction.DETAIL : AgentQueryAction.LIST);
            plan.setToolNames(hasOrder ? List.of("orderDetail") : List.of("listOrders"));
            return plan;
        }
        if (domain == AgentQueryDomain.MEAL_PLAN) return singleToolPlan(plan, AgentQueryAction.LIST, "listMealPlans");
        if (domain == AgentQueryDomain.VERIFICATION) return singleToolPlan(plan, AgentQueryAction.LIST, "listVerifications");
        if (domain == AgentQueryDomain.REFUND) return singleToolPlan(plan, AgentQueryAction.LIST, "listRefunds");
        // 当前分析协议没有套餐 ID、菜品 ID 列表或规则主题，不能伪造可执行计划。
        return null;
    }

    /** 将跨客户排餐过敏语义编译为唯一的范围排餐基础查询，调用图不来自模型。 */
    private AgentQueryPlan mealPlanAllergyPlan(AgentQueryPlan plan, BusinessQuestionAnalysis analysis) {
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V3);
        plan.setDomain(AgentQueryDomain.MEAL_PLAN);
        plan.setAction(AgentQueryAction.LIST);
        AgentQueryFilters modelFilters = analysis.getFilters();
        AgentQueryFilters executableFilters = new AgentQueryFilters();
        if (modelFilters != null) {
            executableFilters.setRecordDate(modelFilters.getRecordDate());
            executableFilters.setMealType(modelFilters.getMealType());
        }
        plan.setEntities(new AgentEntityReference());
        plan.setFilters(executableFilters);
        MealScope scope = analysis.getMealScope();
        if (scope == null && plan.getFilters().getMealType() != null) {
            try { scope = MealScope.valueOf(plan.getFilters().getMealType().trim().toUpperCase()); }
            catch (IllegalArgumentException ignored) { return null; }
        }
        if (scope == null) return null;
        plan.setMealScope(scope);
        if (scope == MealScope.ALL_AVAILABLE) plan.getFilters().setMealType(null);
        else plan.getFilters().setMealType(scope.name());
        plan.setSubjects(analysis.getSubjects()); plan.setRelations(analysis.getRelations());
        plan.setRequestedFacts(analysis.getRequestedFacts()); plan.setOperation(analysis.getOperation()); plan.setGroupBy(analysis.getGroupBy());
        plan.setLimit(50); plan.getFilters().setPage(1); plan.getFilters().setSize(50);
        plan.setToolNames(List.of("listMealPlans"));
        return plan;
    }

    /** 将公共菜单目标映射为唯一登记工具，并将餐次范围写入 QueryPlan 供纠错指纹和执行器共同使用。 */
    private AgentQueryPlan scheduledMenuPlan(AgentQueryPlan plan, BusinessQuestionAnalysis analysis) {
        if (plan.getDomain() != AgentQueryDomain.DISH) return null;
        MealScope scope = analysis.getMealScope() == null ? MealScope.ALL_AVAILABLE : analysis.getMealScope();
        if (scope == MealScope.BREAKFAST) return null;
        plan.setMealScope(scope);
        if (scope == MealScope.ALL_AVAILABLE) plan.getFilters().setMealType(null);
        else plan.getFilters().setMealType(scope.name());
        return singleToolPlan(plan, AgentQueryAction.LIST, "listScheduledDishes");
    }

    /** 组装版本化聚合计划，指标和工具均由服务端目录决定。 */
    private AgentQueryPlan aggregationPlan(AgentQueryPlan plan, BusinessQuestionAnalysis analysis) {
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V2);
        plan.setAction(analysis.getDimensions() == null || analysis.getDimensions().isEmpty() ? AgentQueryAction.SUMMARY : AgentQueryAction.BREAKDOWN);
        plan.setMetricVersion(AgentMetricCatalog.VERSION);
        plan.setTimezone("Asia/Shanghai");
        plan.setLimit(100);
        plan.setToolNames(statisticTools(analysis));
        return plan;
    }

    /** 使用固定动作与白名单工具组装单工具只读计划。 */
    private AgentQueryPlan singleToolPlan(AgentQueryPlan plan, AgentQueryAction action, String toolName) {
        plan.setAction(action);
        plan.setToolNames(List.of(toolName));
        return plan;
    }

    private List<String> statisticTools(BusinessQuestionAnalysis analysis) {
        if (analysis.getMetrics() == null || analysis.getMetrics().isEmpty()) return List.of();
        switch (analysis.getMetrics().get(0)) {
            case ACTIVE_CUSTOMER_COUNT:
                return List.of("getActiveCustomerSummary");
            case EXPIRING_ORDER_COUNT:
                return List.of("getExpiringOrderSummary");
            case MEAL_PLAN_FAILURE_COUNT:
                return List.of("getMealPlanFailureSummary");
            default:
                return List.of("getDailyCustomerWorkload");
        }
    }

    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
}
