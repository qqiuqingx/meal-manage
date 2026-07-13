package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanSummaryDto;

/** Agent 排餐只读查询服务。 */
public interface AgentMealPlanQueryService {
    /** 按客户、日期和餐次或客户排餐记录 ID 查询。 */
    AgentListResultDto<AgentMealPlanSummaryDto> query(AgentMealPlanQueryRequest request);
}
