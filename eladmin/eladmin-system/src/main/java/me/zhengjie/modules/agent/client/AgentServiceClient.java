package me.zhengjie.modules.agent.client;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;

public interface AgentServiceClient {

    AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request);

    AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId);

    /**
     * 带短期客服访问上下文的聊天调用；兼容旧实现时默认退回两参数调用。
     */
    default AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId, String accessContext) {
        return chatMealPlan(request, requestId);
    }
}
