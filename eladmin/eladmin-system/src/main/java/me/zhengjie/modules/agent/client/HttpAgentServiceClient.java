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
        try {
            ResponseEntity<String> response = restTemplate().postForEntity(buildUrl(), requestEntity(request), String.class);
            return JSON.parseObject(response.getBody(), AgentDiagnosisResponse.class);
        } catch (RestClientException ex) {
            log.warn("call agent-service failed, fallback to manual diagnosis hint", ex);
            return fallback(request);
        }
    }

    private HttpEntity<String> requestEntity(AgentDiagnosisRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
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

    private AgentDiagnosisResponse fallback(AgentDiagnosisRequest request) {
        AgentDiagnosisResponse response = new AgentDiagnosisResponse();
        response.setCustomerId(request.getCustomerId());
        response.setRecordDate(request.getRecordDate());
        response.setMealType(request.getMealType());
        response.setFallback(true);
        response.setSummary("智能排查服务暂不可用，请先按客户、订单、排餐记录和菜单配置人工核对。");
        return response;
    }
}
