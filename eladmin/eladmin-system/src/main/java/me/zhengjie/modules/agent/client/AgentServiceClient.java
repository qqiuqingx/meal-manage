package me.zhengjie.modules.agent.client;

import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;

public interface AgentServiceClient {

    AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request);
}
