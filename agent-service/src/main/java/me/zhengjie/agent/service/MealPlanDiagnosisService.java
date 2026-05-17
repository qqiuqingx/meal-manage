package me.zhengjie.agent.service;

import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;

public interface MealPlanDiagnosisService {

    /**
     * 接收诊断请求并返回结构化诊断结果。
     */
    DiagnosisResponse diagnose(DiagnosisRequest request);
}
