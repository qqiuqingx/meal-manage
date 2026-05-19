package me.zhengjie.modules.agent.client;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
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

    @Value("${agent.service.connect-timeout:3000}")
    private int connectTimeout;

    @Value("${agent.service.read-timeout:15000}")
    private int readTimeout;

    @Override
    public AgentDiagnosisResponse diagnoseMealPlan(AgentDiagnosisRequest request) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        String url = buildUrl();
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

    private HttpEntity<String> requestEntity(AgentDiagnosisRequest request, String requestId) {
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

    private String buildUrl() {
        return trimRight(baseUrl) + "/" + trimLeft(diagnosePath);
    }

    private String trimRight(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String trimLeft(String value) {
        return value == null ? "" : value.replaceAll("^/+", "");
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
}
