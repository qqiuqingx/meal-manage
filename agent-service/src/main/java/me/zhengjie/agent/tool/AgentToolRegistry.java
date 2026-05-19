package me.zhengjie.agent.tool;

import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Spring AI Tool Calling 注册入口。
 */
@Component
public class AgentToolRegistry {

    private final DiagnosisToolDataClient toolDataClient;

    public AgentToolRegistry(DiagnosisToolDataClient toolDataClient) {
        this.toolDataClient = toolDataClient;
    }

    @Tool(name = "getCustomerProfile", description = "查询客户基础档案。仅在需要判断客户排除日期、配送要求、客户状态等客户信息时调用。")
    public Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request) {
        return toolDataClient.getCustomerProfile(request);
    }

    @Tool(name = "listCustomerOrders", description = "查询客户订单列表。仅在需要判断订单是否有效、剩余餐数、套餐信息时调用。")
    public List<Map<String, Object>> listCustomerOrders(DiagnosisToolCustomerOrdersRequest request) {
        return toolDataClient.listCustomerOrders(request);
    }

    @Tool(name = "getMealPlan", description = "查询指定日期和餐次的排餐详情。仅在需要判断排餐是否生成、生成状态、失败原因时调用。")
    public Map<String, Object> getMealPlan(DiagnosisToolMealPlanLookupRequest request) {
        return toolDataClient.getMealPlan(request);
    }

    @Tool(name = "getCandidateDishStats", description = "查询指定日期候选菜统计。仅在需要判断候选菜或套餐过滤后数量时调用。")
    public List<Map<String, Object>> getCandidateDishStats(DiagnosisToolCandidateDishStatsRequest request) {
        return toolDataClient.getCandidateDishStats(request);
    }
}
