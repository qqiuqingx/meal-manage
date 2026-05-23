package me.zhengjie.modules.agent.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.client.AgentServiceClient;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import org.springframework.stereotype.Service;

/**
 * 智能排查后台代理服务。
 */
@Service
@RequiredArgsConstructor
public class AgentDiagnosisFacadeServiceImpl implements AgentDiagnosisFacadeService {

    private final AgentServiceClient agentServiceClient;

    @Override
    public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
        return agentServiceClient.diagnoseMealPlan(request);
    }

    @Override
    public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
        return agentServiceClient.chatMealPlan(request, requestId);
    }
}
