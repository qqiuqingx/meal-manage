package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.AgentActionAudit;
import me.zhengjie.modules.agent.domain.dto.AgentActionAuditQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmRequest;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmResponse;
import me.zhengjie.utils.PageResult;

/**
 * 智能排查动作草稿人工确认服务。
 */
public interface AgentActionConfirmService {

    /**
     * 校验并确认动作草稿，返回审计结果。
     *
     * @param request 动作草稿确认请求
     * @return 确认结果和审计记录 ID
     */
    AgentActionConfirmResponse confirm(AgentActionConfirmRequest request);

    /**
     * 分页查询动作确认审计记录，用于按会话追踪动作建议和人工确认结果。
     *
     * @param criteria 查询条件
     * @return 动作确认审计分页结果
     */
    PageResult<AgentActionAudit> queryAudits(AgentActionAuditQueryCriteria criteria);
}
