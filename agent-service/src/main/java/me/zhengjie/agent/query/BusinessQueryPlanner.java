package me.zhengjie.agent.query;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;

import java.util.List;

/** 将已识别的客服查询类型和会话槽位转换为确定性 QueryPlan。 */
public class BusinessQueryPlanner {
    /**
     * 构造受控业务查询计划，不接受任意工具名、字段名或 SQL 表达式。
     *
     * @param responseType 固定业务查询类型
     * @param slots 已解析会话槽位
     * @return 受控查询计划；非业务查询返回 null
     */
    public AgentQueryPlan plan(String responseType, DiagnosisSlots slots) {
        if (responseType == null || !responseType.startsWith("BUSINESS_QUERY")) return null;
        AgentQueryPlan plan = new AgentQueryPlan();
        AgentEntityReference entities = new AgentEntityReference();
        entities.setCustomerId(slots.getCustomerId()); entities.setCustomerCode(slots.getCustomerCode());
        entities.setCustomerName(slots.getCustomerName());
        entities.setOrderId(slots.getOrderId()); entities.setOrderCode(slots.getOrderCode());
        entities.setMealPlanRecordId(slots.getMealPlanRecordId());
        plan.setEntities(entities);
        if (slots.getOrderStatus() != null) plan.getFilters().setOrderStatus(String.valueOf(slots.getOrderStatus()));
        if (slots.getMealType() != null) plan.getFilters().setMealType(slots.getMealType());
        if (slots.getRecordDate() != null) plan.getFilters().setRecordDate(slots.getRecordDate());
        if (slots.getStartDate() != null) plan.getFilters().setStartDate(slots.getStartDate());
        if (slots.getEndDate() != null) plan.getFilters().setEndDate(slots.getEndDate());
        if ("BUSINESS_QUERY_CUSTOMER_CANDIDATES".equals(responseType)) {
            plan.setDomain(AgentQueryDomain.CUSTOMER);
            plan.setAction(AgentQueryAction.LIST);
            plan.setToolNames(List.of("resolveCustomer"));
        }
        else if ("BUSINESS_QUERY_ORDER".equals(responseType)) {
            plan.setDomain(AgentQueryDomain.ORDER);
            // 已明确订单时必须使用详情工具，保证审计计划与实际调用的内部接口一致。
            if (entities.getOrderId() != null || entities.getOrderCode() != null) {
                plan.setAction(AgentQueryAction.DETAIL);
                plan.setToolNames(List.of("orderDetail"));
            } else {
                plan.setAction(AgentQueryAction.LIST);
                plan.setToolNames(List.of("listOrders"));
            }
        }
        else if ("BUSINESS_QUERY_VERIFICATION".equals(responseType)) { plan.setDomain(AgentQueryDomain.VERIFICATION); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("listVerifications")); }
        else if ("BUSINESS_QUERY_MEAL_PLAN".equals(responseType)) { plan.setDomain(AgentQueryDomain.MEAL_PLAN); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("listMealPlans")); }
        else if ("BUSINESS_QUERY_REFUND".equals(responseType)) { plan.setDomain(AgentQueryDomain.REFUND); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("listRefunds")); }
        else if ("BUSINESS_QUERY_PACKAGE".equals(responseType)) { plan.setDomain(AgentQueryDomain.PACKAGE); plan.setAction(AgentQueryAction.DETAIL); plan.setToolNames(List.of("packageDetail")); }
        else if ("BUSINESS_QUERY_RULE".equals(responseType)) { plan.setDomain(AgentQueryDomain.BUSINESS_RULE); plan.setAction(AgentQueryAction.EXPLAIN); plan.setToolNames(List.of("explainRule")); }
        else if ("BUSINESS_QUERY_DISH".equals(responseType)) { plan.setDomain(AgentQueryDomain.DISH); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("listMealPlans", "listDishes")); }
        else if ("BUSINESS_QUERY_SCHEDULED_MENU".equals(responseType)) { plan.setDomain(AgentQueryDomain.DISH); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("listScheduledDishes")); }
        else if ("BUSINESS_QUERY_OPERATION_DAILY".equals(responseType)) {
            operationPlan(plan, AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT, "getDailyCustomerWorkload");
        }
        else if ("BUSINESS_QUERY_OPERATION_SCHEDULED".equals(responseType)) {
            operationPlan(plan, AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT, "getDailyCustomerWorkload");
        }
        else if ("BUSINESS_QUERY_OPERATION_VERIFIED".equals(responseType)) {
            operationPlan(plan, AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT, "getDailyCustomerWorkload");
        }
        else if ("BUSINESS_QUERY_OPERATION_FAILURE".equals(responseType)) {
            operationPlan(plan, AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT, "getMealPlanFailureSummary");
        }
        else if ("BUSINESS_QUERY_OPERATION_UNSCHEDULED".equals(responseType)) {
            operationPlan(plan, AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT, "getDailyCustomerWorkload");
        }
        else if ("BUSINESS_QUERY_OPERATION_ACTIVE".equals(responseType)) {
            operationPlan(plan, AgentQueryMetric.ACTIVE_CUSTOMER_COUNT, "getActiveCustomerSummary");
        }
        else if ("BUSINESS_QUERY_OPERATION_CUSTOMER_TOTAL".equals(responseType)) {
            operationPlan(plan, AgentQueryMetric.CUSTOMER_PROFILE_COUNT, "getCustomerProfileCount");
        }
        else if ("BUSINESS_QUERY_OPERATION_EXPIRING".equals(responseType)) {
            operationPlan(plan, AgentQueryMetric.EXPIRING_ORDER_COUNT, "getExpiringOrderSummary");
        }
        else if ("BUSINESS_QUERY_DISH_CANDIDATES".equals(responseType)) { plan.setDomain(AgentQueryDomain.DISH); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("previewDishCandidates")); }
        else { plan.setDomain(AgentQueryDomain.CUSTOMER); plan.setAction(AgentQueryAction.OVERVIEW); plan.setToolNames(List.of("customerOverview")); }
        return plan;
    }

    /** 组装统计 QueryPlan 2.0，工具名只能由服务端固定映射产生。 */
    private void operationPlan(AgentQueryPlan plan, AgentQueryMetric metric, String toolName) {
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V2);
        plan.setDomain(AgentQueryDomain.OPERATION_STATISTICS);
        plan.setAction(AgentQueryAction.SUMMARY);
        plan.setMetrics(List.of(metric));
        plan.setMetricVersion(AgentMetricCatalog.VERSION);
        plan.setTimezone("Asia/Shanghai");
        plan.setLimit(100);
        plan.setToolNames(List.of(toolName));
        plan.setAnalysisSource("RULE");
        plan.setAnalysisConfidence(0.95D);
    }
}
