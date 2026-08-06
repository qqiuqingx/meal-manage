package me.zhengjie.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.security.AgentAccessContextHolder;
import me.zhengjie.agent.tool.input.ExplainBusinessRuleInput;
import me.zhengjie.agent.tool.input.GetPackageDetailInput;
import me.zhengjie.agent.tool.input.GetServiceCustomerDetailInput;
import me.zhengjie.agent.tool.input.ListMealPlansInput;
import me.zhengjie.agent.tool.input.ListRefundsInput;
import me.zhengjie.agent.tool.input.ListScheduledDishesInput;
import me.zhengjie.agent.tool.input.ListVerificationsInput;
import me.zhengjie.agent.tool.input.PreviewDishCandidatesInput;
import me.zhengjie.agent.tool.input.QueryBusinessMetricsInput;
import me.zhengjie.agent.tool.input.SearchCustomerProfilesInput;
import me.zhengjie.agent.tool.input.SearchDishesInput;
import me.zhengjie.agent.tool.input.SearchServiceCustomersInput;
import me.zhengjie.agent.tool.output.ToolOutputs;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主系统统一查询 HTTP 适配器。
 *
 * <p>所有路径在方法中固定登记，所有请求都带主系统签发的短期上下文；返回结果先经过敏感字段检查，
 * 再转换为 Agent 专用输出类型。</p>
 */
@Component
public class HttpMainSystemQueryClient implements MainSystemQueryClient {
    private static final Logger log = LoggerFactory.getLogger(HttpMainSystemQueryClient.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String SESSION_ID_HEADER = "X-Agent-Session-Id";
    private static final String ACCESS_CONTEXT_HEADER = "X-Agent-Access-Context";
    private static final String INTERNAL_TOKEN_HEADER = "X-Agent-Internal-Token";
    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper;
    private final SensitiveDataPolicy sensitiveDataPolicy;

    public HttpMainSystemQueryClient(RestClient.Builder builder, AgentProperties properties,
                                     ObjectMapper objectMapper, SensitiveDataPolicy sensitiveDataPolicy) {
        Assert.hasText(properties.getInternalToken(), "agent.internal-token must be configured");
        long configuredTimeout = properties.getChat().getToolLoop().getToolTimeoutMs();
        int timeout = (int) Math.max(100, Math.min(configuredTimeout, 10000));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeout));
        factory.setReadTimeout(Duration.ofMillis(timeout));
        this.restClient = builder.baseUrl(properties.getContextBaseUrl()).requestFactory(factory).build();
        this.internalToken = properties.getInternalToken();
        this.objectMapper = objectMapper;
        this.sensitiveDataPolicy = sensitiveDataPolicy;
    }

    @Override public ToolOutputs.ToolResult<ToolOutputs.CustomerProfile> searchCustomerProfiles(SearchCustomerProfilesInput input) {
        return post("/api/internal/agent/query/customer-profiles/search", input, ToolOutputs.CustomerProfile.class);
    }
    @Override public ToolOutputs.ToolResult<ToolOutputs.ServiceCustomer> searchServiceCustomers(SearchServiceCustomersInput input) {
        return post("/api/internal/agent/query/service-customers/search", input, ToolOutputs.ServiceCustomer.class);
    }
    @Override public ToolOutputs.ToolResult<ToolOutputs.ServiceCustomerDetail> getServiceCustomerDetail(GetServiceCustomerDetailInput input) {
        return postSingle("/api/internal/agent/query/service-customers/detail", input, ToolOutputs.ServiceCustomerDetail.class);
    }
    @Override public ToolOutputs.ToolResult<ToolOutputs.MealPlan> listMealPlans(ListMealPlansInput input) {
        return post("/api/internal/agent/query/meal-plans/list", input, ToolOutputs.MealPlan.class);
    }
    @Override public ToolOutputs.ToolResult<ToolOutputs.Verification> listVerifications(ListVerificationsInput input) {
        return post("/api/internal/agent/query/verifications/list", input, ToolOutputs.Verification.class);
    }
    @Override public ToolOutputs.ToolResult<ToolOutputs.Refund> listRefunds(ListRefundsInput input) {
        return post("/api/internal/agent/query/refunds/list", input, ToolOutputs.Refund.class);
    }
    @Override public ToolOutputs.ToolResult<Object> previewDishCandidates(PreviewDishCandidatesInput input) {
        return postRaw("/api/internal/agent/query/dishes/candidates", input);
    }
    @Override public ToolOutputs.ToolResult<Object> listScheduledDishes(ListScheduledDishesInput input) {
        return postRaw("/api/internal/agent/query/dishes/scheduled", input);
    }
    @Override public ToolOutputs.ToolResult<ToolOutputs.Dish> searchDishes(SearchDishesInput input) {
        return post("/api/internal/agent/query/dishes/search", input, ToolOutputs.Dish.class);
    }
    @Override public ToolOutputs.ToolResult<ToolOutputs.PackageDetail> getPackageDetail(GetPackageDetailInput input) {
        return postSingle("/api/internal/agent/query/packages/detail", input, ToolOutputs.PackageDetail.class);
    }
    @Override public ToolOutputs.ToolResult<ToolOutputs.Metric> queryBusinessMetrics(QueryBusinessMetricsInput input) {
        return postSingle("/api/internal/agent/query/metrics/query", input, ToolOutputs.Metric.class);
    }
    @Override public ToolOutputs.ToolResult<ToolOutputs.BusinessRule> explainBusinessRule(ExplainBusinessRuleInput input) {
        return postSingle("/api/internal/agent/query/rules/explain", input, ToolOutputs.BusinessRule.class);
    }

    /** 调用返回列表项的主系统统一查询接口。 */
    private <T> ToolOutputs.ToolResult<T> post(String path, Object body, Class<T> itemType) {
        return convert(path, body, itemType, false);
    }

    /** 调用返回单个聚合对象的主系统统一查询接口。 */
    private <T> ToolOutputs.ToolResult<T> postSingle(String path, Object body, Class<T> dataType) {
        return convert(path, body, dataType, true);
    }

    /** 调用结构化候选菜或公共菜单聚合接口并保留受控 data 对象。 */
    private ToolOutputs.ToolResult<Object> postRaw(String path, Object body) {
        return convert(path, body, Object.class, false);
    }

    /** 统一添加内部认证上下文、执行响应护栏、记录不含业务正文的调试摘要并转换主系统响应信封。 */
    private <T> ToolOutputs.ToolResult<T> convert(String path, Object body, Class<T> itemType, boolean single) {
        long startedAt = System.nanoTime();
        String requestId = requestId();
        log.info("AGENT_DEBUG_QUERY_REQUEST requestId={} path={} requestType={}",
            requestId, path, typeName(body));
        if (AgentAccessContextHolder.accessContext() == null || AgentAccessContextHolder.sessionId() == null) {
            MainSystemQueryException exception = new MainSystemQueryException("AGENT_QUERY_UNAUTHORIZED");
            logQueryResponse(requestId, path, "FAILED", null, exception.getCode(), null, startedAt);
            throw exception;
        }
        try {
            JsonNode node = restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .header(INTERNAL_TOKEN_HEADER, internalToken)
                .header(REQUEST_ID_HEADER, requestId())
                .header(SESSION_ID_HEADER, AgentAccessContextHolder.sessionId())
                .header(ACCESS_CONTEXT_HEADER, AgentAccessContextHolder.accessContext())
                .body(body).retrieve().body(JsonNode.class);
            sensitiveDataPolicy.assertSafe(node);
            ToolOutputs.ToolResult<T> result = mapResult(node, itemType, single);
            logQueryResponse(requestId, path, "SUCCESS", null, null, result, startedAt);
            return result;
        } catch (RestClientResponseException exception) {
            String code = resolveFailure(exception);
            logQueryResponse(requestId, path, "FAILED", exception.getStatusCode().value(), code, null, startedAt);
            throw new MainSystemQueryException(code, exception);
        } catch (ResourceAccessException exception) {
            String code = isTimeout(exception) ? "TOOL_TIMEOUT" : "TOOL_UNAVAILABLE";
            logQueryResponse(requestId, path, "FAILED", null, code, null, startedAt);
            throw new MainSystemQueryException(code, exception);
        } catch (MainSystemQueryException exception) {
            logQueryResponse(requestId, path, "FAILED", null, exception.getCode(), null, startedAt);
            throw exception;
        } catch (RuntimeException exception) {
            logQueryResponse(requestId, path, "FAILED", null, "TOOL_OUTPUT_INVALID", null, startedAt);
            throw new MainSystemQueryException("TOOL_OUTPUT_INVALID", exception);
        }
    }

    /** 输出不含完整业务 JSON 的主系统查询响应摘要；日志异常不得影响查询结果。 */
    private void logQueryResponse(String requestId, String path, String status, Integer httpStatus,
                                  String errorCode, ToolOutputs.ToolResult<?> response, long startedAt) {
        try {
            long total = response == null ? 0 : response.getTotal();
            int resultCount = response == null ? 0
                : response.getItems().size() + (response.getData() == null ? 0 : 1);
            boolean truncated = response != null && response.isTruncated();
            int warningCount = response == null ? 0 : response.getWarnings().size();
            if ("FAILED".equals(status)) {
                log.warn("AGENT_DEBUG_QUERY_RESPONSE requestId={} path={} status={} httpStatus={} errorCode={} costMs={}",
                    requestId, path, status, httpStatus, errorCode, elapsedMs(startedAt));
            } else {
                log.info("AGENT_DEBUG_QUERY_RESPONSE requestId={} path={} status={} total={} resultCount={} truncated={} warningCount={} costMs={}",
                    requestId, path, status, total, resultCount, truncated, warningCount, elapsedMs(startedAt));
            }
        } catch (RuntimeException ignored) {
            // 调试日志失败不能改变主系统查询结果。
        }
    }

    /** 返回请求 DTO 类型名称；调试日志不记录对象字段和值。 */
    private String typeName(Object value) { return value == null ? "null" : value.getClass().getSimpleName(); }

    /** 计算从查询开始到当前的毫秒耗时。 */
    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    /** 将主系统统一响应映射为 Agent 内部工具结果，丢弃未登记的字段。 */
    private <T> ToolOutputs.ToolResult<T> mapResult(JsonNode node, Class<T> type, boolean single) {
        if (node == null || !node.isObject()) throw new MainSystemQueryException("TOOL_OUTPUT_INVALID");
        ToolOutputs.ToolResult<T> result = new ToolOutputs.ToolResult<>();
        result.setSchemaVersion(text(node, "schemaVersion", "v1"));
        result.setTotal(node.path("total").asLong(0));
        result.setPage(node.path("page").asInt(1));
        result.setSize(node.path("size").asInt(0));
        result.setTruncated(node.path("truncated").asBoolean(false));
        result.setQueriedAt(text(node, "queriedAt", null));
        if (node.has("warnings") && node.get("warnings").isArray()) {
            result.setWarnings(objectMapper.convertValue(node.get("warnings"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        }
        if (!single && node.has("items") && node.get("items").isArray() && type != Object.class) {
            java.util.ArrayList<T> items = new java.util.ArrayList<>();
            for (JsonNode item : node.get("items")) items.add(objectMapper.convertValue(item, type));
            result.setItems(items);
        } else if (!single && node.has("items") && node.get("items").isArray()) {
            result.setItems((List<T>) objectMapper.convertValue(node.get("items"), List.class));
        }
        if (node.has("data") && !node.get("data").isNull()) result.setData(objectMapper.convertValue(node.get("data"), type));
        else if (single && !node.has("data")) result.setData(objectMapper.convertValue(node, type));
        return result;
    }

    /** 读取响应中的可选字符串字段，并在缺失时使用安全默认值。 */
    private String text(JsonNode node, String name, String fallback) {
        return node.has(name) && !node.get(name).isNull() ? node.get(name).asText() : fallback;
    }

    /** 获取当前请求的审计 ID，不从模型输入中读取。 */
    private String requestId() { return MDC.get("requestId") == null ? "" : MDC.get("requestId"); }

    /** 将 HTTP 状态映射为不泄露下游细节的稳定工具故障码。 */
    private String resolveFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401) return "AGENT_QUERY_UNAUTHORIZED";
        if (status == 403) return "AGENT_QUERY_ACCESS_DENIED";
        if (status == 404) return "AGENT_QUERY_NOT_FOUND";
        if (status >= 400 && status < 500) return "AGENT_QUERY_INVALID_REQUEST";
        return "AGENT_QUERY_INTERNAL_ERROR";
    }

    private boolean isTimeout(ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null) { if (cause instanceof SocketTimeoutException) return true; cause = cause.getCause(); }
        return false;
    }
}
