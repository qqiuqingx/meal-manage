package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsDto;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsQuery;

/**
 * 智能排查运营指标服务。
 */
public interface AgentOperationStatsService {

    /**
     * 记录一次诊断结果的运营指标。
     *
     * @param response 诊断结果
     * @param sessionId 会话 ID
     * @param costMs 诊断耗时毫秒
     */
    void recordDiagnosis(AgentDiagnosisResponse response, String sessionId, long costMs);

    /**
     * 查询运营看板统计。
     *
     * @param query 查询条件
     * @return 运营统计结果
     */
    AgentOperationStatsDto stats(AgentOperationStatsQuery query);
}
