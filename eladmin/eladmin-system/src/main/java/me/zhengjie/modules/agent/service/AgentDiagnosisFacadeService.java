package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;

public interface AgentDiagnosisFacadeService {

    AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request);

    AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId);

    /**
     * 带短期客服访问上下文的聊天调用；保留旧接口以兼容既有诊断调用方。
     */
    default AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId, String accessContext) {
        return chatMealPlan(request, requestId);
    }
}
