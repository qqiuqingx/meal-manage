package me.zhengjie.agent.controller;

import jakarta.validation.Valid;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.LlmConnectivityRequest;
import me.zhengjie.agent.domain.dto.LlmConnectivityResponse;
import me.zhengjie.agent.service.LlmConnectivityService;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/agent/meal-plan")
public class MealPlanDiagnosisController {

    private static final Logger log = LoggerFactory.getLogger(MealPlanDiagnosisController.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final MealPlanDiagnosisService diagnosisService;
    private final LlmConnectivityService connectivityService;

    /**
     * 注入诊断服务，控制器只负责接收请求和返回结果。
     */
    public MealPlanDiagnosisController(MealPlanDiagnosisService diagnosisService,
                                       LlmConnectivityService connectivityService) {
        this.diagnosisService = diagnosisService;
        this.connectivityService = connectivityService;
    }

    /**
     * 执行排餐未生成原因诊断。
     */
    @PostMapping("/diagnose")
    public DiagnosisResponse diagnose(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                      @Valid @RequestBody DiagnosisRequest request) {
        String traceId = resolveRequestId(requestId);
        MDC.put(REQUEST_ID_KEY, traceId);
        long start = System.currentTimeMillis();
        try {
            log.info("agent diagnose request received requestId={} customerId={} customerCode={} recordDate={} mealType={}",
                traceId, request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
            DiagnosisResponse response = diagnosisService.diagnose(request);
            log.info("agent diagnose request completed requestId={} customerId={} recordDate={} mealType={} fallback={} reasonCount={} costMs={}",
                traceId, request.getCustomerId(), request.getRecordDate(), request.getMealType(), response.isFallback(),
                response.getReasons() == null ? 0 : response.getReasons().size(), System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException ex) {
            log.error("agent diagnose request failed requestId={} customerId={} customerCode={} recordDate={} mealType={} costMs={} errorType={} errorMessage={}",
                traceId, request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType(),
                System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    /**
     * 测试当前 LLM base-url、key 和模型配置是否可用。
     */
    @PostMapping("/llm/test")
    public LlmConnectivityResponse testLlm(@RequestBody(required = false) LlmConnectivityRequest request) {
        return connectivityService.test(request == null ? new LlmConnectivityRequest() : request);
    }

    private String resolveRequestId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }
}
