package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.agent.service.AgentDiagnosisContextService;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部诊断上下文接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/agent")
public class InternalAgentDiagnosisContextController {

    private static final String REQUEST_ID_KEY = "requestId";

    private final AgentDiagnosisContextService contextService;

    @PostMapping("/meal-plan/context")
    public ResponseEntity<MealPlanDiagnosisContextDto> buildContext(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                    @Validated @RequestBody MealPlanDiagnosisContextRequest request) {
        if (requestId != null && !requestId.trim().isEmpty()) {
            MDC.put(REQUEST_ID_KEY, requestId.trim());
        }
        long start = System.currentTimeMillis();
        try {
            log.info("internal agent context request received requestId={} customerId={} customerCode={} recordDate={} mealType={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
            MealPlanDiagnosisContextDto context = contextService.buildContext(request);
            log.info("internal agent context request completed requestId={} customerId={} recordDate={} mealType={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                    System.currentTimeMillis() - start);
            return ResponseEntity.ok(context);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }
}
