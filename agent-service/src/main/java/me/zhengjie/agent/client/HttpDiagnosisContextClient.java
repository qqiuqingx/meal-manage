package me.zhengjie.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.security.AgentAccessContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.Assert;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String INTERNAL_TOKEN_HEADER = "X-Agent-Internal-Token";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String contextPath;
    private final String internalToken;

    /**
     * 初始化远程上下文接口客户端，并在启动时校验内部 token 已经配置，避免运行期裸奔。
     */
    @Autowired
    public HttpDiagnosisContextClient(RestClient.Builder builder,
                                      ObjectMapper objectMapper,
                                      AgentProperties properties) {
        this(builder, objectMapper, properties.getContextBaseUrl(), properties.getContextPath(),
            properties.getInternalToken());
    }

    /** 测试用显式端点构造器，生产配置统一由 {@link AgentProperties} 注入。 */
    HttpDiagnosisContextClient(RestClient.Builder builder, ObjectMapper objectMapper,
                               String contextBaseUrl, String contextPath, String internalToken) {
        Assert.hasText(internalToken, "agent.internal-token must be configured");
        this.restClient = builder.baseUrl(contextBaseUrl).build();
        this.objectMapper = objectMapper;
        this.contextPath = contextPath;
        this.internalToken = internalToken;
    }

    @Override
    public DiagnosisContextDto fetch(DiagnosisRequest request) {
        long start = System.currentTimeMillis();
        log.info("诊断阶段 stage=远程上下文请求开始 requestId={} path={} recordDate={} mealType={}",
            MDC.get(REQUEST_ID_KEY), contextPath, request.getRecordDate(), request.getMealType());
        Map<String, Object> body = restClient.post()
            .uri(contextPath)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Request-Id", requestId())
            .header(INTERNAL_TOKEN_HEADER, internalToken)
            .headers(headers -> appendAccessContext(headers))
            .body(request)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        log.info("诊断阶段 stage=远程上下文请求完成 requestId={} path={} bodyPresent={} costMs={}",
            MDC.get(REQUEST_ID_KEY), contextPath, body != null, System.currentTimeMillis() - start);
        if (body == null) {
            return new DiagnosisContextDto();
        }
        return objectMapper.convertValue(body, DiagnosisContextDto.class);
    }

    /**
     * 复用 controller 入口生成的 requestId，让 agent-service 和 eladmin-system 两侧日志能串起来。
     */
    private String requestId() {
        String requestId = MDC.get(REQUEST_ID_KEY);
        return requestId == null ? "" : requestId;
    }

    private void appendAccessContext(org.springframework.http.HttpHeaders headers) {
        String accessContext = AgentAccessContextHolder.accessContext();
        String sessionId = AgentAccessContextHolder.sessionId();
        if (accessContext != null) headers.set("X-Agent-Access-Context", accessContext);
        if (sessionId != null) headers.set("X-Agent-Session-Id", sessionId);
    }
}
