package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;

public interface AgentDiagnosisFacadeService {

    AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request);

    /**
     * 使用 v2 可信信封所需的短期访问上下文执行聊天。
     *
     * @param request 主系统会话服务组装的聊天请求
     * @param requestId 请求链路标识
     * @param accessContext 主系统签发的短期访问上下文
     * @return Agent 聊天响应
     */
    AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId,
                                   String accessContext);
}
