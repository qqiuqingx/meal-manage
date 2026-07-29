package me.zhengjie.agent.capability;

import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.application.conversation.ConversationExecutionContext;
import me.zhengjie.agent.query.domain.AgentQueryPlan;

import java.util.Set;

/** 能力处理器的受控登记接口；一个处理器可明确承载多个兼容 profile。 */
public interface CapabilityHandler {
    String handlerId();
    Set<String> plannerProfiles();

    /**
     * 判断处理器是否接受目录已匹配的固定 profile。
     *
     * @param plannerProfile 能力目录声明的规划 profile
     * @param frame 已通过目录约束的语义帧
     * @return 是否可由当前处理器编译
     */
    default boolean supports(String plannerProfile, SemanticRequestFrame frame) {
        return plannerProfile != null && plannerProfiles().contains(plannerProfile);
    }

    /**
     * 将受控语义帧编译成固定查询计划。新增能力可通过新增 Handler 扩展，
     * 不需要修改中心 Planner。
     *
     * @param plannerProfile 能力目录声明的规划 profile
     * @param frame 已通过目录匹配的语义帧
     * @param context 可信会话执行上下文
     * @return 仅含服务端登记工具的查询计划
     */
    default AgentQueryPlan compile(String plannerProfile, SemanticRequestFrame frame,
                                   ConversationExecutionContext context) {
        throw new IllegalArgumentException("CAPABILITY_NOT_AVAILABLE: " + plannerProfile);
    }
}
