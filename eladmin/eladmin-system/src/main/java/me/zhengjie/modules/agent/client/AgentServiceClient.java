package me.zhengjie.modules.agent.client;

import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;

public interface AgentServiceClient {

    AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request);

    /**
     * 使用 v2 可信信封和短期客服访问上下文调用 Agent 聊天接口。
     *
     * @param request 主系统从持久化会话组装的内部请求
     * @param requestId 请求链路标识
     * @param accessContext 主系统签发的短期访问上下文
     * @return Agent v2 聊天响应
     */
    AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId,
                                   String accessContext);
}
