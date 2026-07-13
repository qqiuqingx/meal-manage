package me.zhengjie.agent.query;

import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.tool.AgentBusinessToolExecutor;

import java.util.List;

/**
 * 单轮业务查询编排器，持有该轮唯一的工具执行器，从而统一预算、缓存和失败表达。
 */
public class BusinessQueryOrchestrator {
    private final AgentBusinessToolExecutor executor;

    /**
     * 创建一次聊天请求专用的编排器，实例不得跨请求复用。
     *
     * @param client 主系统受控只读客户端
     * @param validator QueryPlan 校验器
     */
    public BusinessQueryOrchestrator(BusinessQueryDataClient client, AgentQueryPlanValidator validator) {
        this.executor = new AgentBusinessToolExecutor(client, validator);
    }

    /**
     * 按 QueryPlan 调用一个登记工具，并返回包含缓存与部分失败状态的受控结果。
     *
     * @param plan 本轮查询计划
     * @param toolName 登记工具名
     * @param ruleTopic 白名单规则主题
     * @param dishIds 菜品 ID 列表
     * @return 工具执行结果
     */
    public AgentBusinessToolExecutor.ToolExecutionResult execute(AgentQueryPlan plan, String toolName,
                                                                  String ruleTopic, List<Integer> dishIds) {
        return executor.execute(plan, toolName, ruleTopic, dishIds);
    }

    /** 返回该轮实际调用主系统内部工具的次数。 */
    public int getCallCount() {
        return executor.getCallCount();
    }
}
