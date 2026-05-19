package me.zhengjie.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 基于 HTTP 的诊断上下文客户端实现。
 */
@Component
public class HttpDiagnosisContextClient implements DiagnosisContextClient {

    private static final Logger log = LoggerFactory.getLogger(HttpDiagnosisContextClient.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String contextPath;

    public HttpDiagnosisContextClient(RestClient.Builder builder,
                                      ObjectMapper objectMapper,
                                      @Value("${agent.context-base-url:http://localhost:8080}") String contextBaseUrl,
                                      @Value("${agent.context-path:/api/internal/agent/meal-plan/context}") String contextPath) {
        this.restClient = builder.baseUrl(contextBaseUrl).build();
        this.objectMapper = objectMapper;
        this.contextPath = contextPath;
    }

    @Override
    public DiagnosisContextDto fetch(DiagnosisRequest request) {
        long start = System.currentTimeMillis();
        log.info("fetch diagnosis context start requestId={} path={} customerId={} customerCode={} recordDate={} mealType={}",
            MDC.get(REQUEST_ID_KEY), contextPath, request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(),
            request.getMealType());
        Map<String, Object> body = restClient.post()
            .uri(contextPath)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Request-Id", requestId())
            .body(request)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        log.info("fetch diagnosis context completed requestId={} path={} bodyPresent={} costMs={}",
            MDC.get(REQUEST_ID_KEY), contextPath, body != null, System.currentTimeMillis() - start);
        if (body == null) {
            return new DiagnosisContextDto();
        }
        return objectMapper.convertValue(body, DiagnosisContextDto.class);
    }

    private String requestId() {
        String requestId = MDC.get(REQUEST_ID_KEY);
        return requestId == null ? "" : requestId;
    }
}
