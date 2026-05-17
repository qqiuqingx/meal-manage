package me.zhengjie.agent.controller;

import jakarta.validation.Valid;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/meal-plan")
public class MealPlanDiagnosisController {

    private final MealPlanDiagnosisService diagnosisService;

    /**
     * 注入诊断服务，控制器只负责接收请求和返回结果。
     */
    public MealPlanDiagnosisController(MealPlanDiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    /**
     * 执行排餐未生成原因诊断。
     */
    @PostMapping("/diagnose")
    public DiagnosisResponse diagnose(@Valid @RequestBody DiagnosisRequest request) {
        return diagnosisService.diagnose(request);
    }
}
