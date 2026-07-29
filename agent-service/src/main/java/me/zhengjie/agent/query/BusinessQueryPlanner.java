package me.zhengjie.agent.query;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.BusinessResponseTypeCatalog;
import me.zhengjie.agent.tool.ToolCatalog;

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
        BusinessResponseTypeCatalog.Definition definition =
            BusinessResponseTypeCatalog.find(responseType).orElse(null);
        if (definition == null) return null;
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
        if (BusinessResponseTypeCatalog.ORDER.equals(responseType)) {
            plan.setDomain(definition.domain());
            // 已明确订单时必须使用详情工具，保证审计计划与实际调用的内部接口一致。
            if (entities.getOrderId() != null || entities.getOrderCode() != null) {
                plan.setAction(AgentQueryAction.DETAIL);
                plan.setToolNames(List.of(ToolCatalog.ORDER_DETAIL));
            } else {
                plan.setAction(definition.action());
                plan.setToolNames(definition.toolNames());
            }
        } else if (definition.metric() != null) {
            operationPlan(plan, definition.metric(), definition.toolNames().get(0));
        } else {
            plan.setDomain(definition.domain());
            plan.setAction(definition.action());
            plan.setToolNames(definition.toolNames());
        }
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
