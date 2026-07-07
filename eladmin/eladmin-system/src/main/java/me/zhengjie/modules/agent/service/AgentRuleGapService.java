package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.AgentRuleGap;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapResponse;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapStatusRequest;
import me.zhengjie.utils.PageResult;

/**
 * 智能排查规则缺口维护服务。
 */
public interface AgentRuleGapService {

    /**
     * 从客服反馈沉淀规则缺口。
     *
     * @param feedback 已保存的反馈记录
     */
    void createFromFeedback(AgentDiagnosisFeedback feedback);

    /**
     * 分页查询规则缺口。
     *
     * @param criteria 查询条件
     * @return 规则缺口分页结果
     */
    PageResult<AgentRuleGap> query(AgentRuleGapQueryCriteria criteria);

    /**
     * 更新规则缺口状态。
     *
     * @param id 缺口 ID
     * @param request 状态更新请求
     * @return 更新结果
     */
    AgentRuleGapResponse updateStatus(Long id, AgentRuleGapStatusRequest request);
}
