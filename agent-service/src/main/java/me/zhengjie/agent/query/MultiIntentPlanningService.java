package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult;
import me.zhengjie.agent.analysis.SemanticCapabilityCatalog;
import me.zhengjie.agent.analysis.SemanticCapabilityCatalogLoader;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.SemanticGoal;
import me.zhengjie.agent.analysis.domain.SemanticOperation;
import me.zhengjie.agent.analysis.domain.SemanticOutputShape;
import me.zhengjie.agent.query.domain.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 将多个受控语义帧编译为固定查询计划；不从用户文本或模型输出读取工具名。 */
public class MultiIntentPlanningService {
    private final SemanticCapabilityCatalog capabilityCatalog;

    /** 使用内置、版本化的受控能力目录构造规划器。 */
    public MultiIntentPlanningService() { this(new SemanticCapabilityCatalogLoader().load()); }

    /** 注入已校验能力目录，便于应用启动时统一管理目录版本。 */
    public MultiIntentPlanningService(SemanticCapabilityCatalog capabilityCatalog) {
        this.capabilityCatalog = capabilityCatalog;
    }

    /** 编译目前登记的多帧能力；未登记组合返回空计划，调用方应返回能力缺失码。 */
    public List<AgentQueryPlan> plan(ConversationUnderstandingResult understanding) {
        List<AgentQueryPlan> plans = new ArrayList<>();
        if (understanding == null || understanding.getFrames() == null) return plans;
        for (SemanticRequestFrame frame : understanding.getFrames()) {
            Map<String, Object> capability = capabilityCatalog == null ? null : capabilityCatalog.findMatching(frame).orElse(null);
            if (capability == null) return List.of();
            String plannerProfile = String.valueOf(capability.get("plannerProfile"));
            AgentQueryPlan plan = "ACTIVE_CUSTOMER_BALANCE_DETAIL_V1".equals(plannerProfile) ? activeCustomerBalancePlan(frame)
                : customerHistoryPlan(frame, plannerProfile);
            if (plan == null) return List.of();
            plans.add(plan);
        }
        return plans;
    }

    /** 编译单客户订单、核销和退餐明细；实体由调用方从确定性槽位填充，模型不能传工具或 ID。 */
    private AgentQueryPlan customerHistoryPlan(SemanticRequestFrame frame, String plannerProfile) {
        if (frame == null || frame.getGoal() != SemanticGoal.QUERY || frame.getOutputShape() != SemanticOutputShape.DETAIL_LIST
            || !frame.getOperations().contains(SemanticOperation.LIST)) return null;
        AgentQueryPlan plan = new AgentQueryPlan();
        if ("CUSTOMER_ORDER_LIST_V1".equals(plannerProfile) && frame.getTargetEntity() == SemanticEntityType.ORDER) {
            plan.setDomain(AgentQueryDomain.ORDER); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("listOrders"));
        } else if ("CUSTOMER_VERIFICATION_LIST_V1".equals(plannerProfile) && frame.getTargetEntity() == SemanticEntityType.VERIFICATION) {
            plan.setDomain(AgentQueryDomain.VERIFICATION); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("listVerifications"));
        } else if ("CUSTOMER_REFUND_LIST_V1".equals(plannerProfile) && frame.getTargetEntity() == SemanticEntityType.REFUND) {
            plan.setDomain(AgentQueryDomain.REFUND); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("listRefunds"));
        } else if ("CUSTOMER_MEAL_PLAN_LIST_V1".equals(plannerProfile) && frame.getTargetEntity() == SemanticEntityType.MEAL_PLAN) {
            plan.setDomain(AgentQueryDomain.MEAL_PLAN); plan.setAction(AgentQueryAction.LIST); plan.setToolNames(List.of("listMealPlans"));
        } else return null;
        plan.setFilters(frame.getConstraints()); plan.setLimit(50); return plan;
    }
    private AgentQueryPlan activeCustomerBalancePlan(SemanticRequestFrame frame) {
        if (frame == null || frame.getGoal() != SemanticGoal.QUERY || frame.getTargetEntity() != SemanticEntityType.CUSTOMER
            || frame.getScope() == null || frame.getScope().getResolvedHandleId() == null
            || !frame.getOperations().contains(SemanticOperation.PROJECT) || !frame.getOperations().contains(SemanticOperation.GROUP)
            || frame.getOutputShape() != SemanticOutputShape.DETAIL_LIST) return null;
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V2); plan.setDomain(AgentQueryDomain.OPERATION_STATISTICS);
        plan.setAction(AgentQueryAction.BREAKDOWN); plan.setMetrics(List.of(AgentQueryMetric.ACTIVE_CUSTOMER_MEAL_BALANCE_DETAIL));
        plan.setDimensions(List.of(AgentQueryDimension.CUSTOMER)); plan.setMetricVersion(AgentMetricCatalog.VERSION);
        plan.setTimezone("Asia/Shanghai"); plan.setLimit(50); plan.getFilters().setPage(1); plan.getFilters().setSize(50);
        plan.setToolNames(List.of("listActiveCustomerMealBalances"));
        return plan;
    }
}
