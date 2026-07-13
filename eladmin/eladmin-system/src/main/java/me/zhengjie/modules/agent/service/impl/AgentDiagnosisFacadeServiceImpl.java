package me.zhengjie.modules.agent.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.client.AgentServiceClient;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import me.zhengjie.modules.agent.service.AgentOperationStatsService;
import me.zhengjie.modules.agent.security.AgentAccessContext;
import me.zhengjie.modules.agent.security.AgentAccessContextService;
import me.zhengjie.modules.agent.security.AgentQueryPermissionService;
import org.springframework.stereotype.Service;

/**
 * 智能排查后台代理服务。
 */
@Service
@RequiredArgsConstructor
public class AgentDiagnosisFacadeServiceImpl implements AgentDiagnosisFacadeService {

    private final AgentServiceClient agentServiceClient;
    private final AgentOperationStatsService operationStatsService;
    private final AgentAccessContextService accessContextService;
    private final AgentQueryPermissionService queryPermissionService;

    @Override
    public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
        long start = System.currentTimeMillis();
        AgentDiagnosisResponse response = agentServiceClient.diagnoseMealPlan(request);
        operationStatsService.recordDiagnosis(response, null, System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
        return chatMealPlan(request, requestId, null);
    }

    /** {@inheritDoc} */
    @Override
    public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId, String accessContext) {
        long start = System.currentTimeMillis();
        attachAvailableTools(request, requestId, accessContext);
        AgentChatResponse response = agentServiceClient.chatMealPlan(request, requestId, accessContext);
        if (response != null && response.getDiagnosisResult() != null) {
            operationStatsService.recordDiagnosis(response.getDiagnosisResult(), response.getSessionId(), System.currentTimeMillis() - start);
        }
        return response;
    }

    /**
     * 将经主系统签名上下文校验后的工具白名单附带给 Agent，用于在编排前跳过无权限工具。
     * 内部工具接口仍会独立校验该上下文，白名单不承担授权职责。
     */
    private void attachAvailableTools(AgentChatRequest request, String requestId, String accessContext) {
        if (request == null || accessContext == null || accessContext.trim().isEmpty()) return;
        AgentAccessContext context = accessContextService.verify(accessContext, request.getSessionId(), requestId);
        request.setAvailableTools(queryPermissionService.availableToolNames(context));
    }
}
