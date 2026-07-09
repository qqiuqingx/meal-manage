package me.zhengjie.modules.agent.client;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisActionDraftDto;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.utils.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * HTTP 调用独立 agent-service。
 */
@Slf4j
@Component
public class HttpAgentServiceClient implements AgentServiceClient {

    @Value("${agent.service.base-url:http://localhost:18081}")
    private String baseUrl;

    @Value("${agent.service.diagnose-path:/api/agent/meal-plan/diagnose}")
    private String diagnosePath;

    @Value("${agent.service.chat-path:/api/agent/meal-plan/chat}")
    private String chatPath;

    @Value("${agent.service.connect-timeout-ms:${agent.service.connect-timeout:3000}}")
    private int connectTimeout;

    @Value("${agent.service.read-timeout-ms:${agent.service.read-timeout:15000}}")
    private int readTimeout;

    @Value("${agent.service.retry-times:1}")
    private int retryTimes;

    @Value("${agent.service.retry-backoff-ms:300}")
    private long retryBackoffMs;

    private static final String FALLBACK_SOURCE_ELADMIN_CLIENT = "ELADMIN_CLIENT";

    @Override
    public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
        String requestId = resolveRequestId(null);
        String url = buildUrl(diagnosePath);
        log.info("诊断阶段 stage=调用agent-service开始 requestId={} url={} customerId={} customerCode={} recordDate={} mealType={}",
            requestId, url, request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
        int attempts = totalAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long attemptStart = System.currentTimeMillis();
            try {
                ResponseEntity<String> response = restTemplate().postForEntity(url, requestEntity(request, requestId), String.class);
                AgentDiagnosisResponse diagnosisResponse = parseDiagnosisResponse(response.getBody(), requestId);
                log.info("诊断阶段 stage=调用agent-service完成 requestId={} url={} attempt={} status={} fallback={} reasonCount={} costMs={}",
                    requestId, url, attempt, response.getStatusCodeValue(), diagnosisResponse.isFallback(),
                    diagnosisResponse.getReasons() == null ? 0 : diagnosisResponse.getReasons().size(),
                    System.currentTimeMillis() - attemptStart);
                return diagnosisResponse;
            } catch (Exception ex) {
                AgentServiceFailureType failureType = classifyFailure(ex);
                boolean willRetry = shouldRetry(failureType, attempt, attempts);
                logFailure("诊断阶段", requestId, null, url, failureType, attempt, attempts, attemptStart, ex);
                if (willRetry) {
                    sleepBackoff();
                    continue;
                }
                return fallback(request, requestId, failureType);
            }
        }
        return fallback(request, requestId, AgentServiceFailureType.AGENT_SERVICE_UNAVAILABLE);
    }

    @Override
    public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
        String resolvedRequestId = resolveRequestId(requestId);
        String url = buildUrl(chatPath);
        log.info("聊天诊断阶段 stage=调用agent-service开始 requestId={} url={} sessionId={}", resolvedRequestId, url, request.getSessionId());
        int attempts = totalAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long attemptStart = System.currentTimeMillis();
            try {
                ResponseEntity<String> response = restTemplate().postForEntity(url, requestEntity(request, resolvedRequestId), String.class);
                AgentChatResponse chatResponse = parseChatResponse(response.getBody(), resolvedRequestId, request);
                log.info("聊天诊断阶段 stage=调用agent-service完成 requestId={} url={} attempt={} status={} chatStatus={} sessionId={} costMs={}",
                    resolvedRequestId, url, attempt, response.getStatusCodeValue(), chatResponse.getStatus(),
                    chatResponse.getSessionId(), System.currentTimeMillis() - attemptStart);
                return chatResponse;
            } catch (Exception ex) {
                AgentServiceFailureType failureType = classifyFailure(ex);
                boolean willRetry = shouldRetry(failureType, attempt, attempts);
                logFailure("聊天诊断阶段", resolvedRequestId, request.getSessionId(), url, failureType, attempt, attempts, attemptStart, ex);
                if (willRetry) {
                    sleepBackoff();
                    continue;
                }
                return chatFallback(request, resolvedRequestId, failureType);
            }
        }
        return chatFallback(request, resolvedRequestId, AgentServiceFailureType.AGENT_SERVICE_UNAVAILABLE);
    }

    private HttpEntity<String> requestEntity(Object request, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", requestId);
        return new HttpEntity<>(JSON.toJSONString(request), headers);
    }

    protected RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    private String buildUrl(String path) {
        return trimRight(baseUrl) + "/" + trimLeft(path);
    }

    private String trimRight(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String trimLeft(String value) {
        return value == null ? "" : value.replaceAll("^/+", "");
    }

    private String resolveRequestId(String requestId) {
        return requestId == null || requestId.trim().isEmpty() ? UUID.randomUUID().toString() : requestId.trim();
    }

    private AgentDiagnosisResponse parseDiagnosisResponse(String body, String requestId) {
        if (StringUtils.isBlank(body)) {
            throw new AgentServiceBadResponseException("agent-service empty body");
        }
        AgentDiagnosisResponse response = JSON.parseObject(body, AgentDiagnosisResponse.class);
        if (response == null) {
            throw new AgentServiceBadResponseException("agent-service response body parsed to null");
        }
        if (StringUtils.isBlank(response.getRequestId())) {
            response.setRequestId(requestId);
        }
        return response;
    }

    private AgentChatResponse parseChatResponse(String body, String requestId, AgentChatRequest request) {
        if (StringUtils.isBlank(body)) {
            throw new AgentServiceBadResponseException("agent-service empty body");
        }
        AgentChatResponse response = JSON.parseObject(body, AgentChatResponse.class);
        if (response == null) {
            throw new AgentServiceBadResponseException("agent-service response body parsed to null");
        }
        if (StringUtils.isBlank(response.getRequestId())) {
            response.setRequestId(requestId);
        }
        if (StringUtils.isBlank(response.getSessionId())) {
            response.setSessionId(request.getSessionId());
        }
        if (response.getClientMessageId() == null) {
            response.setClientMessageId(request.getClientMessageId());
        }
        return response;
    }

    private AgentDiagnosisResponse fallback(AgentDiagnosisRequest request, String requestId, AgentServiceFailureType failureType) {
        AgentDiagnosisResponse response = new AgentDiagnosisResponse();
        response.setRequestId(requestId);
        response.setCustomerId(request.getCustomerId());
        response.setRecordDate(request.getRecordDate());
        response.setMealType(request.getMealType());
        response.setFallback(true);
        response.setSummary(failureType.getFallbackMessage());
        response.setFallbackReason(failureType.getFallbackMessage());
        response.setFallbackSource(FALLBACK_SOURCE_ELADMIN_CLIENT);
        response.setFailureType(failureType.name());
        response.setConfidence("LOW");
        response.setNextActions(failureType.retryable()
            ? Arrays.asList("核对客户档案", "核对订单有效性", "核对排餐记录", "核对候选菜配置")
            : Arrays.asList("检查客户、日期和餐次是否完整", "确认当前账号具备智能排查访问权限"));
        if (failureType != AgentServiceFailureType.AGENT_SERVICE_4XX) {
            response.setActionDrafts(Collections.singletonList(manualRecheckDraft(request, requestId, failureType)));
        }
        return response;
    }

    /**
     * agent-service 不可用时生成只用于展示的人工复核动作草稿。
     */
    private AgentDiagnosisActionDraftDto manualRecheckDraft(AgentDiagnosisRequest request, String requestId, AgentServiceFailureType failureType) {
        AgentDiagnosisActionDraftDto draft = new AgentDiagnosisActionDraftDto();
        draft.setActionCode("CREATE_MANUAL_RECHECK_TASK");
        draft.setTitle("创建人工复核任务");
        draft.setDescription("诊断服务异常时，创建人工复核任务并附带当前请求上下文。");
        draft.setRiskLevel("LOW");
        draft.setTargetType("RECHECK_TASK");
        draft.setTargetId((request.getRecordDate() == null ? "" : request.getRecordDate()) + "|" + (request.getMealType() == null ? "" : request.getMealType()));
        draft.setBeforeSnapshot(fallbackSnapshot(request, requestId, failureType));
        draft.setAfterPreview(fallbackPreview(request));
        draft.setRequiredPermission("agentDiagnosis:confirm");
        draft.setConfirmApi("/api/agent/action-drafts/confirm");
        return draft;
    }

    /**
     * 生成兜底动作草稿的请求快照。
     */
    private Map<String, Object> fallbackSnapshot(AgentDiagnosisRequest request, String requestId, AgentServiceFailureType failureType) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "eladmin-fallback");
        snapshot.put("requestId", requestId);
        snapshot.put("failureType", failureType.name());
        snapshot.put("customerId", request.getCustomerId());
        snapshot.put("customerCode", request.getCustomerCode());
        snapshot.put("recordDate", request.getRecordDate());
        snapshot.put("mealType", request.getMealType());
        return snapshot;
    }

    /**
     * 生成兜底动作草稿的人工确认预览。
     */
    private Map<String, Object> fallbackPreview(AgentDiagnosisRequest request) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("customerId", request.getCustomerId());
        preview.put("recordDate", request.getRecordDate());
        preview.put("mealType", request.getMealType());
        preview.put("executeMode", "MANUAL_CONFIRM_REQUIRED");
        preview.put("taskType", "MEAL_PLAN_DIAGNOSIS_RECHECK");
        return preview;
    }

    private AgentChatResponse chatFallback(AgentChatRequest request, String requestId, AgentServiceFailureType failureType) {
        AgentChatResponse response = new AgentChatResponse();
        response.setRequestId(requestId);
        response.setSessionId(request.getSessionId());
        response.setClientMessageId(request.getClientMessageId());
        response.setStatus("ERROR");
        response.setAssistantMessage(failureType.getFallbackMessage());
        response.setQuickReplies(Arrays.asList("重新排查", "清空会话"));
        response.setConversationStage("ERROR");
        AgentDiagnosisResponse diagnosisFallback = new AgentDiagnosisResponse();
        diagnosisFallback.setRequestId(requestId);
        diagnosisFallback.setFallback(true);
        diagnosisFallback.setSummary(failureType.getFallbackMessage());
        diagnosisFallback.setFallbackReason(failureType.getFallbackMessage());
        diagnosisFallback.setFallbackSource(FALLBACK_SOURCE_ELADMIN_CLIENT);
        diagnosisFallback.setFailureType(failureType.name());
        diagnosisFallback.setConfidence("LOW");
        response.setDiagnosisResult(diagnosisFallback);
        return response;
    }

    private AgentServiceFailureType classifyFailure(Exception ex) {
        if (ex instanceof AgentServiceBadResponseException) {
            return AgentServiceFailureType.AGENT_SERVICE_BAD_RESPONSE;
        }
        if (ex instanceof HttpStatusCodeException statusException) {
            if (statusException.getStatusCode().is4xxClientError()) {
                return AgentServiceFailureType.AGENT_SERVICE_4XX;
            }
            if (statusException.getStatusCode().is5xxServerError()) {
                return AgentServiceFailureType.AGENT_SERVICE_5XX;
            }
            return AgentServiceFailureType.AGENT_SERVICE_BAD_RESPONSE;
        }
        if (ex instanceof ResourceAccessException) {
            Throwable rootCause = rootCause(ex);
            if (rootCause instanceof SocketTimeoutException || containsIgnoreCase(ex.getMessage(), "timed out")) {
                return AgentServiceFailureType.AGENT_SERVICE_TIMEOUT;
            }
            if (rootCause instanceof ConnectException
                || rootCause instanceof UnknownHostException
                || rootCause instanceof NoRouteToHostException
                || containsIgnoreCase(ex.getMessage(), "connection refused")) {
                return AgentServiceFailureType.AGENT_SERVICE_UNAVAILABLE;
            }
            return AgentServiceFailureType.AGENT_SERVICE_UNAVAILABLE;
        }
        if (ex instanceof RestClientException) {
            return AgentServiceFailureType.AGENT_SERVICE_BAD_RESPONSE;
        }
        return AgentServiceFailureType.AGENT_SERVICE_BAD_RESPONSE;
    }

    private boolean shouldRetry(AgentServiceFailureType failureType, int attempt, int totalAttempts) {
        return failureType.retryable() && attempt < totalAttempts;
    }

    private int totalAttempts() {
        return Math.max(retryTimes, 0) + 1;
    }

    private void sleepBackoff() {
        if (retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void logFailure(String phase,
                            String requestId,
                            String sessionId,
                            String url,
                            AgentServiceFailureType failureType,
                            int attempt,
                            int totalAttempts,
                            long attemptStart,
                            Exception ex) {
        log.warn("{} stage=调用agent-service失败 requestId={} sessionId={} url={} failureType={} attempt={}/{} costMs={} errorType={} errorMessage={}",
            phase, requestId, sessionId, url, failureType.name(), attempt, totalAttempts,
            System.currentTimeMillis() - attemptStart, ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && keyword != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    private enum AgentServiceFailureType {
        AGENT_SERVICE_TIMEOUT(true, "智能排查服务响应超时，已生成兜底人工复核建议。"),
        AGENT_SERVICE_UNAVAILABLE(true, "智能排查服务不可用，已生成兜底人工复核建议。"),
        AGENT_SERVICE_BAD_RESPONSE(false, "智能排查服务返回异常，已生成兜底人工复核建议。"),
        AGENT_SERVICE_4XX(false, "智能排查请求未通过服务校验，请检查输入信息。"),
        AGENT_SERVICE_5XX(true, "智能排查服务内部异常，已生成兜底人工复核建议。");

        private final boolean retryable;
        private final String fallbackMessage;

        AgentServiceFailureType(boolean retryable, String fallbackMessage) {
            this.retryable = retryable;
            this.fallbackMessage = fallbackMessage;
        }

        public boolean retryable() {
            return retryable;
        }

        public String getFallbackMessage() {
            return fallbackMessage;
        }
    }

    private static class AgentServiceBadResponseException extends RuntimeException {

        AgentServiceBadResponseException(String message) {
            super(message);
        }
    }
}
