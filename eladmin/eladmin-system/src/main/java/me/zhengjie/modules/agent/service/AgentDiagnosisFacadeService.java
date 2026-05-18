package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;

public interface AgentDiagnosisFacadeService {

    AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request);
}
