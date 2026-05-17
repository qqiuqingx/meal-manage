package me.zhengjie.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 基于 HTTP 的诊断上下文客户端实现。
 */
@Component
public class HttpDiagnosisContextClient implements DiagnosisContextClient {

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
        Map<String, Object> body = restClient.post()
            .uri(contextPath)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        if (body == null) {
            return new DiagnosisContextDto();
        }
        return objectMapper.convertValue(body, DiagnosisContextDto.class);
    }
}
