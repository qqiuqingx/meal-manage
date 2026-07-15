package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.SemanticGoal;
import me.zhengjie.agent.analysis.domain.SemanticOperation;
import me.zhengjie.agent.analysis.domain.SemanticOutputShape;
import me.zhengjie.agent.query.domain.*;
import java.util.ArrayList;
import java.util.List;

/** 将多个受控语义帧编译为固定查询计划；不从用户文本或模型输出读取工具名。 */
public class MultiIntentPlanningService {
    /** 编译目前登记的多帧能力；未登记组合返回空计划，调用方应返回能力缺失码。 */
    public List<AgentQueryPlan> plan(ConversationUnderstandingResult understanding) {
        List<AgentQueryPlan> plans = new ArrayList<>();
        if (understanding == null || understanding.getFrames() == null) return plans;
        for (SemanticRequestFrame frame : understanding.getFrames()) {
            AgentQueryPlan plan = activeCustomerBalancePlan(frame);
            if (plan == null) return List.of();
            plans.add(plan);
        }
        return plans;
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
