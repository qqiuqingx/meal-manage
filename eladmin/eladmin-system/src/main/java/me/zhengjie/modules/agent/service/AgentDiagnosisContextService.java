package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;

/**
 * 诊断上下文聚合服务
 */
public interface AgentDiagnosisContextService {

    MealPlanDiagnosisContextDto buildContext(MealPlanDiagnosisContextRequest request);
}
