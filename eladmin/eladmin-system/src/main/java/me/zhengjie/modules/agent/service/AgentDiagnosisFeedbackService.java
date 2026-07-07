package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackStatsDto;
import me.zhengjie.utils.PageResult;

/**
 * 智能排查客服反馈服务。
 */
public interface AgentDiagnosisFeedbackService {

    /**
     * 保存客服对诊断结果的反馈。
     *
     * @param request 反馈请求
     * @return 保存结果
     */
    AgentDiagnosisFeedbackResponse submit(AgentDiagnosisFeedbackRequest request);

    /**
     * 分页查询客服反馈记录。
     *
     * @param criteria 查询条件
     * @return 反馈分页结果
     */
    PageResult<AgentDiagnosisFeedback> query(AgentDiagnosisFeedbackQueryCriteria criteria);

    /**
     * 统计客服反馈采纳率和真实原因分布。
     *
     * @param criteria 查询条件
     * @return 反馈统计结果
     */
    AgentDiagnosisFeedbackStatsDto stats(AgentDiagnosisFeedbackQueryCriteria criteria);
}
