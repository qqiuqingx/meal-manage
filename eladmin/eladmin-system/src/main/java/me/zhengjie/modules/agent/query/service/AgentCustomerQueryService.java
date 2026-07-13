package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerCandidateDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerOverviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;

/**
 * Agent 客户只读查询服务；只返回强类型、脱敏且无金额字段的数据契约。
 */
public interface AgentCustomerQueryService {

    /**
     * 按客户 ID、编号或姓名解析客户；姓名命中多个客户时返回候选项而不自行选择。
     *
     * @param customerId 客户 ID，可为空
     * @param customerCode 客户编号，可为空
     * @param customerName 客户姓名关键字，可为空
     * @return 有限数量的候选客户
     */
    AgentListResultDto<AgentCustomerCandidateDto> resolve(Long customerId, String customerCode, String customerName);

    /**
     * 查询单个客户的档案、饮食限制、地址、订单数量与餐数余额摘要。
     *
     * @param customerId 客户 ID，可为空
     * @param customerCode 客户编号，可为空
     * @return 客户不存在时 present=false 的概览对象
     */
    AgentCustomerOverviewDto getOverview(Long customerId, String customerCode);
}
