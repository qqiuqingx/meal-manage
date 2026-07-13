package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.AgentBusinessQueryAudit;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditStatsDto;
import me.zhengjie.utils.PageResult;

/** 记录 Agent 业务只读查询的最小可追溯审计信息。 */
public interface AgentBusinessQueryAuditService {
    /**
     * 记录一轮业务查询审计；非业务查询响应不写入。
     *
     * @param response 已标准化聊天响应
     * @param operator 当前客服账号
     * @param costMs 本轮查询耗时
     */
    void record(AgentChatResponse response, String operator, long costMs);

    /**
     * 分页查询业务只读查询审计记录，供运营和问题追溯使用。
     *
     * @param criteria 审计查询条件
     * @return 分页审计记录
     */
    PageResult<AgentBusinessQueryAudit> query(AgentBusinessQueryAuditCriteria criteria);

    /**
     * 统计业务只读查询次数、失败、部分成功、缓存命中和工具分布。
     *
     * @param criteria 审计统计过滤条件
     * @return 业务查询审计统计结果
     */
    AgentBusinessQueryAuditStatsDto stats(AgentBusinessQueryAuditCriteria criteria);
}
