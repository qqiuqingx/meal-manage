package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台智能排查接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/meal-plan")
public class AgentDiagnosisController {

    private final AgentDiagnosisFacadeService diagnosisFacadeService;

    @PostMapping("/diagnose")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<AgentDiagnosisResponse> diagnoseMealPlan(@Validated @RequestBody AgentDiagnosisRequest request) {
        return ResponseEntity.ok(diagnosisFacadeService.diagnoseMealPlan(request));
    }
}
