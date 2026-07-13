package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.AgentQueryAction;

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
