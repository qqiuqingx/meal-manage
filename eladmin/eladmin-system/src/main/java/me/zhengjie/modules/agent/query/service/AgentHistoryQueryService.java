package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentHistoryQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentRefundLogDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentVerificationLogDto;

/** Agent 核销和退餐只读查询服务。 */
public interface AgentHistoryQueryService {
    /** 查询未删除核销日志。 */
    AgentListResultDto<AgentVerificationLogDto> listVerifications(AgentHistoryQueryRequest request);
    /** 查询退餐记录，绝不返回退款金额。 */
    AgentListResultDto<AgentRefundLogDto> listRefunds(AgentHistoryQueryRequest request);
}
