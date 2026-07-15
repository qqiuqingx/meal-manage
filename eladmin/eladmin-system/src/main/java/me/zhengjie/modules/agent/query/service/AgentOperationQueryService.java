package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentDailyCustomerStatsDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationCountDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationDailyRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationOrderRequest;

/** 主系统运营指标的只读聚合服务，页面和 Agent 应共用该口径。 */
public interface AgentOperationQueryService {
    /** 查询指定日期的已排餐、已核销、待核销与失败聚合。 */
    AgentDailyCustomerStatsDto dailyCustomers(AgentOperationDailyRequest request);
    /** 查询存在进行中且仍有餐数余额的客户去重数。 */
    AgentOperationCountDto activeCustomers();
    /** 查询当前授权数据范围内已录入的客户档案总数。 */
    AgentOperationCountDto customerProfileCount();
    /** 查询日期范围内到期的进行中订单数。 */
    AgentOperationCountDto expiringOrders(AgentOperationOrderRequest request);
}
