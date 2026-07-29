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
import me.zhengjie.agent.security.AgentAccessContextHolder;
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
        putDiagnosisMdc(request.getRecordDate(), request.getMealType(), "CONTROLLER_RECEIVED");
        long start = System.currentTimeMillis();
        try {
            log.info("诊断阶段 stage=接收请求 requestId={} recordDate={} mealType={}",
                traceId, request.getRecordDate(), request.getMealType());
            DiagnosisResponse response = diagnosisService.diagnose(request);
            MDC.put(FALLBACK_KEY, String.valueOf(response.isFallback()));
            MDC.put(FALLBACK_REASON_KEY, safe(response.getFallbackReason()));
            MDC.put(STAGE_KEY, "CONTROLLER_COMPLETED");
            log.info("诊断阶段 stage=请求完成 requestId={} recordDate={} mealType={} fallback={} reasonCount={} costMs={}",
                traceId, request.getRecordDate(), request.getMealType(), response.isFallback(),
                response.getReasons() == null ? 0 : response.getReasons().size(), System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException ex) {
            MDC.put(STAGE_KEY, "CONTROLLER_FAILED");
            log.error("诊断阶段 stage=请求失败 requestId={} recordDate={} mealType={} costMs={} errorType={}",
                traceId, request.getRecordDate(), request.getMealType(),
                System.currentTimeMillis() - start, ex.getClass().getSimpleName());
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
                                  @RequestHeader(value = "X-Agent-Access-Context", required = false) String accessContext,
                                  @Valid @RequestBody AgentChatRequest request) {
        String traceId = resolveRequestId(requestId);
        MDC.put(REQUEST_ID_KEY, traceId);
        MDC.put(STAGE_KEY, "CHAT_CONTROLLER_RECEIVED");
        long start = System.currentTimeMillis();
        try {
            AgentAccessContextHolder.bind(accessContext, request.getSessionId());
            AgentAccessContextHolder.bindAvailableTools(request.getAvailableTools());
            log.info("聊天诊断阶段 stage=接收请求 requestId={}", traceId);
            AgentChatResponse response = chatService.chat(request);
            response.setRequestId(traceId);
            MDC.put(STAGE_KEY, safe(response.getConversationStage()));
            MDC.put(FALLBACK_KEY, String.valueOf(response.getDiagnosisResult() != null && response.getDiagnosisResult().isFallback()));
            MDC.put(FALLBACK_REASON_KEY, response.getDiagnosisResult() == null ? "" : safe(response.getDiagnosisResult().getFallbackReason()));
            log.info("聊天诊断阶段 stage=请求完成 requestId={} status={} costMs={}",
                traceId, response.getStatus(), System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException ex) {
            MDC.put(STAGE_KEY, "CHAT_CONTROLLER_FAILED");
            log.error("聊天诊断阶段 stage=请求失败 requestId={} costMs={} errorType={}",
                traceId, System.currentTimeMillis() - start, ex.getClass().getSimpleName());
            throw ex;
        } finally {
            AgentAccessContextHolder.clear();
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

    /**
     * 仅写入非客户标识类诊断字段，避免通过 MDC 日志泄露客户或会话信息。
     */
    private void putDiagnosisMdc(String recordDate, String mealType, String stage) {
        MDC.put(RECORD_DATE_KEY, safe(recordDate));
        MDC.put(MEAL_TYPE_KEY, safe(mealType));
        MDC.put(STAGE_KEY, safe(stage));
    }

    private void clearDiagnosticMdc() {
        MDC.remove(REQUEST_ID_KEY);
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
