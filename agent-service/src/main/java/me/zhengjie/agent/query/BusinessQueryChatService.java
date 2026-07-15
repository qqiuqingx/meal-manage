package me.zhengjie.agent.query;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.tool.AgentBusinessToolExecutor.ToolExecutionResult;

import java.util.List;
import java.util.ArrayList;

/** 通用只读业务查询聊天服务，负责单轮编排器、QueryPlan 与工具执行边界。 */
public class BusinessQueryChatService {
    private final BusinessQueryDataClient client;
    private final AgentQueryPlanValidator validator;
    private final BusinessQueryResponseFactory responseFactory;

    /** 创建业务查询聊天服务。 */
    public BusinessQueryChatService(BusinessQueryDataClient client, AgentQueryPlanValidator validator,
                                    BusinessAnswerValidator answerValidator) {
        this.client = client;
        this.validator = validator;
        this.responseFactory = new BusinessQueryResponseFactory(answerValidator);
    }

    /** 判断当前环境是否已配置主系统业务查询客户端。 */
    public boolean isAvailable() { return client != null; }

    /** 为一次聊天请求创建独立编排器，禁止跨请求复用缓存。 */
    public BusinessQueryOrchestrator createOrchestrator() {
        return client == null ? null : new BusinessQueryOrchestrator(client, validator);
    }

    /** 构造固定业务响应类型对应的受控 QueryPlan。 */
    public AgentQueryPlan plan(String responseType, DiagnosisSlots slots) {
        return responseFactory.plan(responseType, slots);
    }

    /** 执行已登记工具；未配置客户端时返回稳定部分失败，不访问下游。 */
    public ToolExecutionResult execute(BusinessQueryOrchestrator orchestrator, String responseType,
                                       DiagnosisSlots slots, String toolName, String ruleTopic, List<Integer> dishIds) {
        if (orchestrator == null) return ToolExecutionResult.failure("BUSINESS_QUERY_CLIENT_UNAVAILABLE");
        return orchestrator.execute(plan(responseType, slots), toolName, ruleTopic, dishIds);
    }

    /**
     * 执行已由受控问题分析生成的 QueryPlan 2.0，避免在执行前回退为字符串响应类型规划。
     *
     * @param orchestrator 当前请求专用编排器
     * @param queryPlan 已登记指标、过滤条件和工具名的查询计划
     * @param toolName 待调用的登记工具名
     * @param ruleTopic 规则主题，仅规则查询使用
     * @param dishIds 菜品 ID，仅菜品查询使用
     * @return 工具执行结果及受控告警
     */
    public ToolExecutionResult execute(BusinessQueryOrchestrator orchestrator, AgentQueryPlan queryPlan,
                                       String toolName, String ruleTopic, List<Integer> dishIds) {
        if (orchestrator == null || queryPlan == null) return ToolExecutionResult.failure("BUSINESS_QUERY_CLIENT_UNAVAILABLE");
        return orchestrator.execute(queryPlan, toolName, ruleTopic, dishIds);
    }

    /**
     * 将一个或多个业务工具的缓存命中、部分失败和稳定告警合并到受控聊天响应。
     * 权限不足时隐藏对象存在性和明细结论，其他失败统一降级为不完整结果提示。
     *
     * @param response 已由响应工厂组装的聊天响应
     * @param executions 本轮关联的工具执行结果
     */
    public void applyToolExecution(AgentChatResponse response, ToolExecutionResult... executions) {
        if (response == null || executions == null || executions.length == 0) return;
        List<String> warnings = new ArrayList<>(response.getWarnings() == null ? List.of() : response.getWarnings());
        boolean partial = response.isPartial();
        boolean cached = response.isCached();
        for (ToolExecutionResult execution : executions) {
            if (execution == null) continue;
            partial = partial || execution.partial();
            cached = cached || execution.cached();
            if (execution.warnings() != null) {
                execution.warnings().forEach(warning -> { if (!warnings.contains(warning)) warnings.add(warning); });
            }
        }
        response.setPartial(partial);
        response.setCached(cached);
        response.setWarnings(warnings);
        if (partial) {
            boolean permissionDenied = warnings.contains("TOOL_PERMISSION_DENIED");
            boolean invalidPlan = warnings.contains("PLAN_INVALID");
            response.setAssistantMessage(permissionDenied
                ? "当前账号缺少查询该类业务数据的权限，未返回对象是否存在或相关明细的结论。"
                : invalidPlan
                    ? "查询条件校验未通过，本次未执行业务查询，请调整查询条件后重试。"
                    : "部分查询未完成，当前不能给出完整结论；请稍后重试或到业务页面核对。");
        }
    }

    /** 返回统一响应组装器。 */
    public BusinessQueryResponseFactory responseFactory() { return responseFactory; }
}
