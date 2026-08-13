package me.zhengjie.modules.agent.client;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
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

    @Value("${agent.service.chat-path:/api/agent/v2/chat}")
    private String chatPath;

    @Value("${agent.service.connect-timeout-ms:${agent.service.connect-timeout:3000}}")
    private int connectTimeout;

    @Value("${agent.service.read-timeout-ms:${agent.service.read-timeout:15000}}")
    private int readTimeout;

    @Value("${agent.service.retry-times:1}")
    private int retryTimes;

    @Value("${agent.service.retry-backoff-ms:300}")
    private long retryBackoffMs;

    /** {@inheritDoc} */
    @Override
    public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId, String accessContext) {
        String resolvedRequestId = resolveRequestId(requestId);
        String url = buildUrl(chatPath);
        log.info("聊天诊断阶段 stage=调用agent-service开始 requestId={} url={}", resolvedRequestId, url);
        int attempts = totalAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long attemptStart = System.currentTimeMillis();
            try {
                Map<String, Object> payload = buildChatEnvelope(request);
                ResponseEntity<String> response = restTemplate().postForEntity(url,
                    requestEntity(payload, resolvedRequestId, accessContext), String.class);
                AgentChatResponse chatResponse = parseChatResponse(response.getBody(), resolvedRequestId, request);
                log.info("聊天诊断阶段 stage=调用agent-service完成 requestId={} url={} attempt={} status={} chatStatus={} costMs={}",
                    resolvedRequestId, url, attempt, response.getStatusCodeValue(), chatResponse.getStatus(),
                    System.currentTimeMillis() - attemptStart);
                return chatResponse;
            } catch (Exception ex) {
                AgentServiceFailureType failureType = classifyFailure(ex);
                boolean willRetry = shouldRetry(failureType, attempt, attempts);
                logFailure("聊天诊断阶段", resolvedRequestId, url, failureType, attempt, attempts, attemptStart, ex);
                if (willRetry) {
                    sleepBackoff();
                    continue;
                }
                return chatFallback(request, resolvedRequestId, failureType);
            }
        }
        return chatFallback(request, resolvedRequestId, AgentServiceFailureType.AGENT_SERVICE_UNAVAILABLE);
    }

    /**
     * 构建调用 agent-service 的请求，并仅通过 HTTP Header 透传短期客服访问上下文。
     */
    private HttpEntity<String> requestEntity(Object request, String requestId, String accessContext) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", requestId);
        if (accessContext != null && !accessContext.trim().isEmpty()) {
            headers.set("X-Agent-Access-Context", accessContext);
        }
        return new HttpEntity<>(JSON.toJSONString(request), headers);
    }

    /**
     * 将主系统持久化的聊天命令转换为 v2 可信执行信封。
     *
     * <p>Map 仅保留在 HTTP 适配层，以兼容 Java 8 主系统与 Java 17 Agent 的独立 DTO 栈。</p>
     */
    private Map<String, Object> buildChatEnvelope(AgentChatRequest request) {
        Map<String, Object> messageRequest = new LinkedHashMap<>();
        messageRequest.put("sessionId", request.getSessionId());
        messageRequest.put("clientMessageId", request.getClientMessageId());
        messageRequest.put("message", request.getMessage());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("contractVersion", "v2");
        envelope.put("messageRequest", messageRequest);
        envelope.put("contextSnapshot", request.getContextSlots());
        envelope.put("availableTools", request.getAvailableTools());
        envelope.put("lastBusinessQueryContext", request.getLastBusinessQueryContext());
        envelope.put("formDraftContext", request.getFormDraftContext());
        envelope.put("sessionVersion", request.getSessionVersion());
        return envelope;
    }

    /** 创建带连接与读取超时的下游 Agent HTTP 客户端。 */
    protected RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    /** 拼接主系统配置地址和固定 Agent v2 路径。 */
    private String buildUrl(String path) {
        return trimRight(baseUrl) + "/" + trimLeft(path);
    }

    /** 去除 URL 基地址末尾斜杠。 */
    private String trimRight(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    /** 去除接口路径开头斜杠，避免重复分隔符。 */
    private String trimLeft(String value) {
        return value == null ? "" : value.replaceAll("^/+", "");
    }

    /** 复用请求 ID 或生成下游调用所需的稳定关联 ID。 */
    private String resolveRequestId(String requestId) {
        return requestId == null || requestId.trim().isEmpty() ? UUID.randomUUID().toString() : requestId.trim();
    }

    /** 解析 Agent v2 响应并补齐主系统侧缺失的关联字段。 */
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

    /** 根据下游失败类型生成不含内部细节的前端兜底响应。 */
    private AgentChatResponse chatFallback(AgentChatRequest request, String requestId, AgentServiceFailureType failureType) {
        AgentChatResponse response = new AgentChatResponse();
        response.setRequestId(requestId);
        response.setSessionId(request.getSessionId());
        response.setClientMessageId(request.getClientMessageId());
        response.setStatus("ERROR");
        response.setAssistantMessage(failureType.getFallbackMessage());
        response.setConversationStage("ERROR");
        response.setWarnings(java.util.Collections.singletonList(failureType.name()));
        response.setPartial(true);
        return response;
    }

    /** 将 HTTP、网络和响应解析异常映射为可审计的稳定失败类型。 */
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

    /** 判断当前失败类型和尝试次数是否允许重试。 */
    private boolean shouldRetry(AgentServiceFailureType failureType, int attempt, int totalAttempts) {
        return failureType.retryable() && attempt < totalAttempts;
    }

    /** 计算包含首次调用在内的总尝试次数。 */
    private int totalAttempts() {
        return Math.max(retryTimes, 0) + 1;
    }

    /** 按配置执行可中断的重试退避等待。 */
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

    /**
     * 记录下游调用失败的稳定分类和异常类型，不输出会话标识、响应体或异常消息。
     */
    private void logFailure(String phase,
                            String requestId,
                            String url,
                            AgentServiceFailureType failureType,
                            int attempt,
                            int totalAttempts,
                            long attemptStart,
                            Exception ex) {
        log.warn("{} stage=调用agent-service失败 requestId={} url={} failureType={} attempt={}/{} costMs={} errorType={}",
            phase, requestId, url, failureType.name(), attempt, totalAttempts,
            System.currentTimeMillis() - attemptStart, ex.getClass().getSimpleName());
    }

    /** 获取异常链最底层原因，仅供失败分类使用。 */
    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** 判断异常文本是否包含指定的不区分大小写关键词。 */
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

        /** 返回该失败类型是否允许调用备用 provider。 */
        public boolean retryable() {
            return retryable;
        }

        /** 返回该失败类型对应的安全前端提示。 */
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
