package me.zhengjie.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.security.AgentAccessContextHolder;
import me.zhengjie.agent.tool.input.formdraft.SaveFormDraftInput;
import me.zhengjie.agent.tool.output.FormDraftToolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Set;

/** 固定调用主系统表单草稿保存接口的 HTTP 适配器。 */
@Component
public class HttpMainSystemFormDraftClient implements MainSystemFormDraftClient {
    private static final Logger log = LoggerFactory.getLogger(HttpMainSystemFormDraftClient.class);
    private static final String PATH = "/api/internal/agent/form-drafts:save";
    private static final Set<String> DRAFT_CODES = Set.of(
        "DRAFT_VERSION_CONFLICT", "DRAFT_TYPE_MISMATCH", "DRAFT_EXPIRED", "DRAFT_NOT_EDITABLE",
        "DRAFT_SCHEMA_UNSUPPORTED", "DRAFT_PAYLOAD_INVALID", "DRAFT_UNKNOWN_FIELD", "FORM_DRAFT_NOT_FOUND");
    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    /** 创建只允许固定主系统地址和路径的草稿客户端。 */
    public HttpMainSystemFormDraftClient(RestClient.Builder builder, AgentProperties properties, ObjectMapper objectMapper) {
        Assert.hasText(properties.getInternalToken(), "agent.internal-token must be configured");
        int timeout = (int) Math.max(100, Math.min(properties.getChat().getToolLoop().getToolTimeoutMs(), 10000));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeout));
        factory.setReadTimeout(Duration.ofMillis(timeout));
        this.restClient = builder.baseUrl(properties.getContextBaseUrl()).requestFactory(factory).build();
        this.internalToken = properties.getInternalToken();
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public FormDraftToolOutput save(SaveFormDraftInput input) {
        if (AgentAccessContextHolder.accessContext() == null || AgentAccessContextHolder.sessionId() == null
            || AgentAccessContextHolder.clientMessageId() == null) {
            throw new MainSystemQueryException("AGENT_QUERY_UNAUTHORIZED");
        }
        try {
            ObjectNode request = buildRequest(input);
            FormDraftToolOutput result = restClient.post().uri(PATH).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Agent-Internal-Token", internalToken)
                .header("X-Request-Id", requestId())
                .header("X-Agent-Session-Id", AgentAccessContextHolder.sessionId())
                .header("X-Agent-Access-Context", AgentAccessContextHolder.accessContext())
                .body(request).retrieve().body(FormDraftToolOutput.class);
            if (result == null) throw new MainSystemQueryException("TOOL_OUTPUT_INVALID");
            log.info("FORM_DRAFT_SAVE requestId={} status={} draftId={} revision={}",
                requestId(), result.getStatus(), result.getDraftId(), result.getRevision());
            return result;
        } catch (RestClientResponseException exception) {
            throw new MainSystemQueryException(resolveFailure(exception), exception);
        } catch (ResourceAccessException exception) {
            throw new MainSystemQueryException(isTimeout(exception) ? "TOOL_TIMEOUT" : "TOOL_UNAVAILABLE", exception);
        }
    }

    /** 使用可信线程上下文组装主系统请求，模型不能控制会话或消息幂等键。 */
    ObjectNode buildRequest(SaveFormDraftInput input) {
        if (AgentAccessContextHolder.sessionId() == null || AgentAccessContextHolder.clientMessageId() == null) {
            throw new MainSystemQueryException("AGENT_QUERY_UNAUTHORIZED");
        }
        ObjectNode request = objectMapper.valueToTree(input);
        request.put("sourceSessionId", AgentAccessContextHolder.sessionId());
        request.put("clientMessageId", AgentAccessContextHolder.clientMessageId());
        if ("CREATE_ORDER".equals(input.getDraftType())) request.set("payload", request.remove("orderPayload"));
        else request.set("payload", request.remove("customerWithOrderPayload"));
        request.remove("customerWithOrderPayload");
        request.remove("orderPayload");
        return request;
    }

    /** 从标准 Spring 错误体中提取登记错误码。 */
    private String resolveFailure(RestClientResponseException exception) {
        try {
            com.fasterxml.jackson.databind.JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            String value = body.path("message").asText(body.path("error").asText());
            for (String code : DRAFT_CODES) if (value != null && value.contains(code)) return code;
        } catch (Exception ignored) { }
        if (exception.getStatusCode().value() == 403) return "AGENT_QUERY_ACCESS_DENIED";
        if (exception.getStatusCode().value() == 404) return "FORM_DRAFT_NOT_FOUND";
        return exception.getStatusCode().is4xxClientError() ? "AGENT_QUERY_INVALID_REQUEST" : "AGENT_QUERY_INTERNAL_ERROR";
    }

    /** 判断请求异常链中是否包含 socket 超时。 */
    private boolean isTimeout(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) { if (cause instanceof SocketTimeoutException) return true; cause = cause.getCause(); }
        return false;
    }

    /** 获取当前链路请求 ID。 */
    private String requestId() { return MDC.get("requestId") == null ? "" : MDC.get("requestId"); }
}
