package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerInsightRequest;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerMealSummaryResponse;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerOrderSummaryResponse;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerVerificationSummaryResponse;

/**
 * 客户信息查询聚合服务
 * 为智能排查 Agent 提供客户维度的确定性业务数据查询
 * @author qqx
 * @date 2026-07-09
 */
public interface AgentCustomerInsightService {

    /**
     * 获取客户餐数汇总
     * 含有效订单剩余餐数、累计核销数和订单明细
     */
    AgentCustomerMealSummaryResponse getMealSummary(AgentCustomerInsightRequest request);

    /**
     * 获取客户核销统计
     * 含累计核销统计和最近核销记录
     */
    AgentCustomerVerificationSummaryResponse getVerificationSummary(AgentCustomerInsightRequest request);

    /**
     * 获取客户订单列表
     * 返回客户的所有订单及餐数信息
     */
    AgentCustomerOrderSummaryResponse getOrderSummary(AgentCustomerInsightRequest request);
}
