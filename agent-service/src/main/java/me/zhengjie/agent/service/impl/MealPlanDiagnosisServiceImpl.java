package me.zhengjie.agent.service.impl;

import me.zhengjie.agent.context.DiagnosisContextBuilder;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.orchestrator.MealPlanDiagnosisOrchestrator;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.springframework.stereotype.Service;

@Service
public class MealPlanDiagnosisServiceImpl implements MealPlanDiagnosisService {

    private final DiagnosisContextBuilder contextBuilder;
    private final MealPlanDiagnosisOrchestrator orchestrator;

    /**
     * 组装上下文构建器和诊断编排器，串起完整诊断链路。
     */
    public MealPlanDiagnosisServiceImpl(DiagnosisContextBuilder contextBuilder,
                                        MealPlanDiagnosisOrchestrator orchestrator) {
        this.contextBuilder = contextBuilder;
        this.orchestrator = orchestrator;
    }

    /**
     * 根据请求构建上下文并输出诊断结果。
     */
    @Override
    public DiagnosisResponse diagnose(DiagnosisRequest request) {
        DiagnosisContextDto context = contextBuilder.build(request);
        return orchestrator.orchestrate(context);
    }
}
