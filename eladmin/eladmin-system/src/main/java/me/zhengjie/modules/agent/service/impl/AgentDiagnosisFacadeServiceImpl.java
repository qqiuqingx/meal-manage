package me.zhengjie.modules.agent.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.client.AgentServiceClient;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import me.zhengjie.modules.agent.service.AgentOperationStatsService;
import org.springframework.stereotype.Service;

/**
 * 智能排查后台代理服务。
 */
@Service
@RequiredArgsConstructor
public class AgentDiagnosisFacadeServiceImpl implements AgentDiagnosisFacadeService {

    private final AgentServiceClient agentServiceClient;
    private final AgentOperationStatsService operationStatsService;

    @Override
    public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
        long start = System.currentTimeMillis();
        AgentDiagnosisResponse response = agentServiceClient.diagnoseMealPlan(request);
        operationStatsService.recordDiagnosis(response, null, System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
        long start = System.currentTimeMillis();
        AgentChatResponse response = agentServiceClient.chatMealPlan(request, requestId);
        if (response != null && response.getDiagnosisResult() != null) {
            operationStatsService.recordDiagnosis(response.getDiagnosisResult(), response.getSessionId(), System.currentTimeMillis() - start);
        }
        return response;
    }
}
