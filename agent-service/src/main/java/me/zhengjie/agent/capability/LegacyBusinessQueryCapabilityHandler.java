package me.zhengjie.agent.capability;

import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.SemanticGoal;
import me.zhengjie.agent.analysis.domain.SemanticOperation;
import me.zhengjie.agent.analysis.domain.SemanticOutputShape;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.application.conversation.ConversationExecutionContext;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.tool.ToolCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 迁移适配器：现有受控 QueryPlan 编译器暂时承载目录中的已发布 profile。 */
@Component
public class LegacyBusinessQueryCapabilityHandler implements CapabilityHandler {
    public String handlerId() { return "legacy-business-query"; }
    public Set<String> plannerProfiles() {
        return Set.of("ACTIVE_CUSTOMER_BALANCE_DETAIL_V1", "ACTIVE_ORDER_DETAIL_V1", "CUSTOMER_ORDER_LIST_V1", "CUSTOMER_VERIFICATION_LIST_V1",
            "CUSTOMER_REFUND_LIST_V1", "CUSTOMER_MEAL_PLAN_LIST_V1");
    }

    /**
     * 将已发布 profile 编译成固定查询计划；工具名只由服务端 profile 决定。
     *
     * @param plannerProfile 能力目录 profile
     * @param frame 已校验语义帧
     * @param context 可信执行上下文
     * @return 固定查询计划
     */
    @Override
    public AgentQueryPlan compile(String plannerProfile, SemanticRequestFrame frame,
                                  ConversationExecutionContext context) {
        if ("ACTIVE_CUSTOMER_BALANCE_DETAIL_V1".equals(plannerProfile)) return activeCustomerBalancePlan(frame);
        if ("ACTIVE_ORDER_DETAIL_V1".equals(plannerProfile)) return activeOrderDetailPlan(frame);
        return customerHistoryPlan(frame, plannerProfile);
    }

    private AgentQueryPlan customerHistoryPlan(SemanticRequestFrame frame, String plannerProfile) {
        if (frame == null || frame.getGoal() != SemanticGoal.QUERY
            || frame.getOutputShape() != SemanticOutputShape.DETAIL_LIST
            || !frame.getOperations().contains(SemanticOperation.LIST)) {
            throw unavailable(plannerProfile);
        }
        AgentQueryPlan plan = new AgentQueryPlan();
        if ("CUSTOMER_ORDER_LIST_V1".equals(plannerProfile) && frame.getTargetEntity() == SemanticEntityType.ORDER) {
            plan.setDomain(AgentQueryDomain.ORDER);
            plan.setAction(AgentQueryAction.LIST);
            plan.setToolNames(List.of(ToolCatalog.LIST_ORDERS));
        } else if ("CUSTOMER_VERIFICATION_LIST_V1".equals(plannerProfile)
            && frame.getTargetEntity() == SemanticEntityType.VERIFICATION) {
            plan.setDomain(AgentQueryDomain.VERIFICATION);
            plan.setAction(AgentQueryAction.LIST);
            plan.setToolNames(List.of(ToolCatalog.LIST_VERIFICATIONS));
        } else if ("CUSTOMER_REFUND_LIST_V1".equals(plannerProfile)
            && frame.getTargetEntity() == SemanticEntityType.REFUND) {
            plan.setDomain(AgentQueryDomain.REFUND);
            plan.setAction(AgentQueryAction.LIST);
            plan.setToolNames(List.of(ToolCatalog.LIST_REFUNDS));
        } else if ("CUSTOMER_MEAL_PLAN_LIST_V1".equals(plannerProfile)
            && frame.getTargetEntity() == SemanticEntityType.MEAL_PLAN) {
            plan.setDomain(AgentQueryDomain.MEAL_PLAN);
            plan.setAction(AgentQueryAction.LIST);
            plan.setToolNames(List.of(ToolCatalog.LIST_MEAL_PLANS));
        } else {
            throw unavailable(plannerProfile);
        }
        plan.setFilters(frame.getConstraints());
        plan.setLimit(50);
        return plan;
    }

    private AgentQueryPlan activeCustomerBalancePlan(SemanticRequestFrame frame) {
        if (frame == null || frame.getGoal() != SemanticGoal.QUERY
            || frame.getTargetEntity() != SemanticEntityType.CUSTOMER || frame.getScope() == null
            || frame.getScope().getResolvedHandleId() == null
            || !frame.getOperations().contains(SemanticOperation.PROJECT)
            || !frame.getOperations().contains(SemanticOperation.GROUP)
            || frame.getOutputShape() != SemanticOutputShape.DETAIL_LIST) {
            throw unavailable("ACTIVE_CUSTOMER_BALANCE_DETAIL_V1");
        }
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V2);
        plan.setDomain(AgentQueryDomain.OPERATION_STATISTICS);
        plan.setAction(AgentQueryAction.BREAKDOWN);
        plan.setMetrics(List.of(AgentQueryMetric.ACTIVE_CUSTOMER_MEAL_BALANCE_DETAIL));
        plan.setDimensions(List.of(AgentQueryDimension.CUSTOMER));
        plan.setMetricVersion(AgentMetricCatalog.VERSION);
        plan.setTimezone("Asia/Shanghai");
        plan.setLimit(50);
        plan.getFilters().setPage(1);
        plan.getFilters().setSize(50);
        plan.setToolNames(List.of(ToolCatalog.LIST_ACTIVE_CUSTOMER_MEAL_BALANCES));
        return plan;
    }

    /** 将已解析的进行中订单集合句柄编译为固定 status=1 的受控分页查询。 */
    private AgentQueryPlan activeOrderDetailPlan(SemanticRequestFrame frame) {
        if (frame == null || frame.getGoal() != SemanticGoal.QUERY
            || frame.getTargetEntity() != SemanticEntityType.ORDER || frame.getScope() == null
            || frame.getScope().getResolvedHandleId() == null
            || frame.getOperations().stream().noneMatch(operation -> operation == SemanticOperation.LIST
                || operation == SemanticOperation.PROJECT)
            || frame.getOutputShape() != SemanticOutputShape.DETAIL_LIST) {
            throw unavailable("ACTIVE_ORDER_DETAIL_V1");
        }
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setDomain(AgentQueryDomain.ORDER);
        plan.setAction(AgentQueryAction.LIST);
        plan.setDetailLevel("DETAIL");
        plan.setLimit(20);
        plan.getFilters().setOrderStatus("1");
        plan.getFilters().setPage(1);
        plan.getFilters().setSize(20);
        plan.setToolNames(List.of(ToolCatalog.LIST_ORDERS));
        return plan;
    }

    private IllegalArgumentException unavailable(String plannerProfile) {
        return new IllegalArgumentException("CAPABILITY_NOT_AVAILABLE: " + plannerProfile);
    }
}
