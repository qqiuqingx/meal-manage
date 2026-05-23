package me.zhengjie.modules.agent.client;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

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

    @Value("${agent.service.connect-timeout:3000}")
    private int connectTimeout;

    @Value("${agent.service.read-timeout:15000}")
    private int readTimeout;

    @Override
    public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
        String requestId = resolveRequestId(null);
        long start = System.currentTimeMillis();
        String url = buildUrl(diagnosePath);
        try {
            log.info("诊断阶段 stage=调用agent-service开始 requestId={} url={} customerId={} customerCode={} recordDate={} mealType={}",
                    requestId, url, request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
            ResponseEntity<String> response = restTemplate().postForEntity(url, requestEntity(request, requestId), String.class);
            AgentDiagnosisResponse diagnosisResponse = JSON.parseObject(response.getBody(), AgentDiagnosisResponse.class);
            log.info("诊断阶段 stage=调用agent-service完成 requestId={} url={} status={} fallback={} reasonCount={} costMs={}",
                    requestId, url, response.getStatusCodeValue(), diagnosisResponse != null && diagnosisResponse.isFallback(),
                    diagnosisResponse == null || diagnosisResponse.getReasons() == null ? 0 : diagnosisResponse.getReasons().size(),
                    System.currentTimeMillis() - start);
            return diagnosisResponse;
        } catch (RestClientException ex) {
            log.warn("诊断阶段 stage=调用agent-service失败并回退人工提示 requestId={} url={} customerId={} customerCode={} recordDate={} mealType={} costMs={} errorType={} errorMessage={}",
                    requestId, url, request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType(),
                    System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return fallback(request, requestId);
        }
    }

    @Override
    public AgentChatResponse chatMealPlan(AgentChatRequest request, String requestId) {
        String resolvedRequestId = resolveRequestId(requestId);
        long start = System.currentTimeMillis();
        String url = buildUrl(chatPath);
        try {
            log.info("聊天诊断阶段 stage=调用agent-service开始 requestId={} url={} sessionId={}", resolvedRequestId, url, request.getSessionId());
            ResponseEntity<String> response = restTemplate().postForEntity(url, requestEntity(request, resolvedRequestId), String.class);
            AgentChatResponse chatResponse = JSON.parseObject(response.getBody(), AgentChatResponse.class);
            if (chatResponse != null && chatResponse.getRequestId() == null) {
                chatResponse.setRequestId(resolvedRequestId);
            }
            log.info("聊天诊断阶段 stage=调用agent-service完成 requestId={} url={} status={} chatStatus={} sessionId={} costMs={}",
                    resolvedRequestId, url, response.getStatusCodeValue(), chatResponse == null ? null : chatResponse.getStatus(),
                    chatResponse == null ? null : chatResponse.getSessionId(), System.currentTimeMillis() - start);
            return chatResponse;
        } catch (RestClientException ex) {
            log.warn("聊天诊断阶段 stage=调用agent-service失败并回退 requestId={} url={} sessionId={} costMs={} errorType={} errorMessage={}",
                    resolvedRequestId, url, request.getSessionId(), System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return chatFallback(request, resolvedRequestId);
        }
    }

    private HttpEntity<String> requestEntity(Object request, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", requestId);
        return new HttpEntity<>(JSON.toJSONString(request), headers);
    }

    private RestTemplate restTemplate() {
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

    private AgentDiagnosisResponse fallback(AgentDiagnosisRequest request, String requestId) {
        AgentDiagnosisResponse response = new AgentDiagnosisResponse();
        response.setRequestId(requestId);
        response.setCustomerId(request.getCustomerId());
        response.setRecordDate(request.getRecordDate());
        response.setMealType(request.getMealType());
        response.setFallback(true);
        response.setSummary("智能排查服务暂不可用，请先按客户、订单、排餐记录和菜单配置人工核对。");
        return response;
    }

    private AgentChatResponse chatFallback(AgentChatRequest request, String requestId) {
        AgentChatResponse response = new AgentChatResponse();
        response.setRequestId(requestId);
        response.setSessionId(request.getSessionId());
        response.setStatus("ERROR");
        response.setAssistantMessage("智能排查服务暂不可用，请先按客户、订单、排餐记录和菜单配置人工核对。");
        response.setQuickReplies(java.util.List.of("重新排查", "清空会话"));
        return response;
    }
}
