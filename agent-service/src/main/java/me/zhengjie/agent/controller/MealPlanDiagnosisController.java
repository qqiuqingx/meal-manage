package me.zhengjie.agent.controller;

import jakarta.validation.Valid;
import me.zhengjie.agent.chat.MealPlanChatService;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
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
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String CUSTOMER_ID_KEY = "customerId";
    private static final String CUSTOMER_CODE_KEY = "customerCode";
    private static final String RECORD_DATE_KEY = "recordDate";
    private static final String MEAL_TYPE_KEY = "mealType";
    private static final String STAGE_KEY = "stage";
    private static final String FALLBACK_KEY = "fallback";
    private static final String FALLBACK_REASON_KEY = "fallbackReason";

    private final MealPlanDiagnosisService diagnosisService;
    private final LlmConnectivityService connectivityService;
    private final MealPlanChatService chatService;

    /**
     * 注入诊断服务，控制器只负责接收请求和返回结果。
     */
    public MealPlanDiagnosisController(MealPlanDiagnosisService diagnosisService,
                                       LlmConnectivityService connectivityService,
                                       MealPlanChatService chatService) {
        this.diagnosisService = diagnosisService;
        this.connectivityService = connectivityService;
        this.chatService = chatService;
    }

    /**
     * 执行排餐未生成原因诊断。
     */
    @PostMapping("/diagnose")
    public DiagnosisResponse diagnose(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                      @Valid @RequestBody DiagnosisRequest request) {
        String traceId = resolveRequestId(requestId);
        MDC.put(REQUEST_ID_KEY, traceId);
        putDiagnosisMdc(request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType(), null, "CONTROLLER_RECEIVED");
        long start = System.currentTimeMillis();
        try {
            log.info("诊断阶段 stage=接收请求 requestId={} customerId={} customerCode={} recordDate={} mealType={}",
                traceId, request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
            DiagnosisResponse response = diagnosisService.diagnose(request);
            MDC.put(FALLBACK_KEY, String.valueOf(response.isFallback()));
            MDC.put(FALLBACK_REASON_KEY, safe(response.getFallbackReason()));
            MDC.put(STAGE_KEY, "CONTROLLER_COMPLETED");
            log.info("诊断阶段 stage=请求完成 requestId={} customerId={} recordDate={} mealType={} fallback={} reasonCount={} costMs={}",
                traceId, request.getCustomerId(), request.getRecordDate(), request.getMealType(), response.isFallback(),
                response.getReasons() == null ? 0 : response.getReasons().size(), System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException ex) {
            MDC.put(STAGE_KEY, "CONTROLLER_FAILED");
            log.error("诊断阶段 stage=请求失败 requestId={} customerId={} customerCode={} recordDate={} mealType={} costMs={} errorType={} errorMessage={}",
                traceId, request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType(),
                System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        } finally {
            clearDiagnosticMdc();
        }
    }

    /**
     * 聊天式排餐诊断。
     */
    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                  @Valid @RequestBody AgentChatRequest request) {
        String traceId = resolveRequestId(requestId);
        MDC.put(REQUEST_ID_KEY, traceId);
        MDC.put(SESSION_ID_KEY, safe(request.getSessionId()));
        MDC.put(STAGE_KEY, "CHAT_CONTROLLER_RECEIVED");
        long start = System.currentTimeMillis();
        try {
            log.info("聊天诊断阶段 stage=接收请求 requestId={} sessionId={}", traceId, request.getSessionId());
            AgentChatResponse response = chatService.chat(request);
            response.setRequestId(traceId);
            MDC.put(SESSION_ID_KEY, safe(response.getSessionId()));
            MDC.put(STAGE_KEY, safe(response.getConversationStage()));
            MDC.put(FALLBACK_KEY, String.valueOf(response.getDiagnosisResult() != null && response.getDiagnosisResult().isFallback()));
            MDC.put(FALLBACK_REASON_KEY, response.getDiagnosisResult() == null ? "" : safe(response.getDiagnosisResult().getFallbackReason()));
            log.info("聊天诊断阶段 stage=请求完成 requestId={} sessionId={} status={} costMs={}",
                traceId, response.getSessionId(), response.getStatus(), System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException ex) {
            MDC.put(STAGE_KEY, "CHAT_CONTROLLER_FAILED");
            log.error("聊天诊断阶段 stage=请求失败 requestId={} sessionId={} costMs={} errorType={} errorMessage={}",
                traceId, request.getSessionId(), System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        } finally {
            clearDiagnosticMdc();
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

    private void putDiagnosisMdc(Long customerId,
                                 String customerCode,
                                 String recordDate,
                                 String mealType,
                                 String sessionId,
                                 String stage) {
        MDC.put(CUSTOMER_ID_KEY, customerId == null ? "" : String.valueOf(customerId));
        MDC.put(CUSTOMER_CODE_KEY, safe(customerCode));
        MDC.put(RECORD_DATE_KEY, safe(recordDate));
        MDC.put(MEAL_TYPE_KEY, safe(mealType));
        MDC.put(SESSION_ID_KEY, safe(sessionId));
        MDC.put(STAGE_KEY, safe(stage));
    }

    private void clearDiagnosticMdc() {
        MDC.remove(REQUEST_ID_KEY);
        MDC.remove(SESSION_ID_KEY);
        MDC.remove(CUSTOMER_ID_KEY);
        MDC.remove(CUSTOMER_CODE_KEY);
        MDC.remove(RECORD_DATE_KEY);
        MDC.remove(MEAL_TYPE_KEY);
        MDC.remove(STAGE_KEY);
        MDC.remove(FALLBACK_KEY);
        MDC.remove(FALLBACK_REASON_KEY);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
