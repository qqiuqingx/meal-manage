package me.zhengjie.agent.query.client;

import me.zhengjie.agent.security.AgentAccessContextHolder;
import me.zhengjie.agent.query.client.dto.DishCandidatePreviewResponse;
import me.zhengjie.agent.query.client.dto.CustomerOverviewResponse;
import me.zhengjie.agent.query.client.dto.OrderListResponse;
import me.zhengjie.agent.query.client.dto.OrderSummaryResponse;
import me.zhengjie.agent.query.client.dto.MealPlanListResponse;
import me.zhengjie.agent.query.client.dto.VerificationListResponse;
import me.zhengjie.agent.query.client.dto.RefundListResponse;
import me.zhengjie.agent.query.client.dto.CustomerCandidateListResponse;
import me.zhengjie.agent.query.client.dto.PackageSpecResponse;
import me.zhengjie.agent.query.client.dto.BusinessRuleResponse;
import me.zhengjie.agent.query.client.dto.DishListResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 主系统通用只读查询 HTTP 客户端；请求仅使用固定路径与强类型字段名。
 */
@Component
public class HttpBusinessQueryDataClient implements BusinessQueryDataClient {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();
    private static final Pattern MAINLAND_PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private final RestClient restClient;
    private final String internalToken;

    public HttpBusinessQueryDataClient(RestClient.Builder builder,
                                       @Value("${agent.context-base-url:http://localhost:8080}") String baseUrl,
                                       @Value("${agent.internal-token}") String internalToken,
                                       @Value("${agent.business-query-timeout-ms:3000}") long timeoutMs) {
        Assert.hasText(internalToken, "agent.internal-token must be configured");
        int safeTimeout = (int) Math.max(100, Math.min(timeoutMs, 10000));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(safeTimeout));
        requestFactory.setReadTimeout(Duration.ofMillis(safeTimeout));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) {
        return resolveCustomerTyped(customerId, customerCode, customerName).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public CustomerCandidateListResponse resolveCustomerTyped(Long customerId, String customerCode, String customerName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("customerCode", customerCode);
        body.put("customerName", customerName);
        return post("/api/internal/agent/query/customer/resolve", body, CustomerCandidateListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> customerOverview(Long customerId, String customerCode) {
        return customerOverviewTyped(customerId, customerCode).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public CustomerOverviewResponse customerOverviewTyped(Long customerId, String customerCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("customerCode", customerCode);
        return post("/api/internal/agent/query/customer/overview", body, CustomerOverviewResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) {
        return listOrdersTyped(customerId, status, page, size).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public OrderListResponse listOrdersTyped(Long customerId, Integer status, int page, int size) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("status", status);
        body.put("page", page);
        body.put("size", size);
        return post("/api/internal/agent/query/orders/list", body, OrderListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) {
        return orderDetailTyped(orderId, orderCode, customerId).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public OrderSummaryResponse orderDetailTyped(Long orderId, String orderCode, Long customerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId); body.put("orderCode", orderCode); body.put("customerId", customerId);
        return post("/api/internal/agent/query/orders/detail", body, OrderSummaryResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) {
        return listVerifications(customerId, orderId, mealType, limit, null, null);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit, String startDate, String endDate) {
        return listVerificationsTyped(customerId, orderId, mealType, limit, startDate, endDate).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public VerificationListResponse listVerificationsTyped(Long customerId, Long orderId, String mealType, int limit, String startDate, String endDate) {
        Map<String, Object> body = historyBody(customerId, orderId, limit);
        body.put("mealType", mealType);
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        return post("/api/internal/agent/query/verifications/list", body, VerificationListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) {
        return listRefunds(customerId, orderId, limit, null, null);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit, String startDate, String endDate) {
        return listRefundsTyped(customerId, orderId, limit, startDate, endDate).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public RefundListResponse listRefundsTyped(Long customerId, Long orderId, int limit, String startDate, String endDate) {
        Map<String, Object> body = historyBody(customerId, orderId, limit);
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        return post("/api/internal/agent/query/refunds/list", body, RefundListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) {
        return listMealPlans(customerId, recordDate, mealType, null);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType, Long customerMealPlanId) {
        return listMealPlansTyped(customerId, recordDate, mealType, customerMealPlanId).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public MealPlanListResponse listMealPlansTyped(Long customerId, String recordDate, String mealType, Long customerMealPlanId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("recordDate", recordDate);
        body.put("mealType", mealType);
        body.put("customerMealPlanId", customerMealPlanId);
        return post("/api/internal/agent/query/meal-plans/list", body, MealPlanListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public MealPlanListResponse listMealPlansRangeTyped(Long customerId, String recordDate, String mealType, int page, int size) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId); body.put("recordDate", recordDate); body.put("mealType", mealType);
        body.put("page", page); body.put("size", size);
        return post("/api/internal/agent/query/meal-plans/list", body, MealPlanListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> packageDetail(Long parentPackageId) {
        return packageDetailTyped(parentPackageId).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public PackageSpecResponse packageDetailTyped(Long parentPackageId) {
        return post("/api/internal/agent/query/packages/detail", Map.of("parentPackageId", parentPackageId), PackageSpecResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> explainRule(String topic) {
        return explainRuleTyped(topic).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public BusinessRuleResponse explainRuleTyped(String topic) {
        return post("/api/internal/agent/query/rules/explain", Map.of("topic", topic), BusinessRuleResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listDishes(List<Integer> dishIds) {
        return listDishesTyped(dishIds).toPresentationMap();
    }

    /** {@inheritDoc} */
    @Override
    public DishListResponse listDishesTyped(List<Integer> dishIds) {
        return post("/api/internal/agent/query/dishes/list", Map.of("dishIds", dishIds), DishListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listScheduledDishes(String recordDate, String mealType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recordDate", recordDate); body.put("mealType", mealType);
        return post("/api/internal/agent/query/dishes/scheduled", body, Map.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listScheduledDishes(String recordDate, List<String> mealTypes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recordDate", recordDate); body.put("mealTypes", mealTypes);
        return post("/api/internal/agent/query/dishes/scheduled", body, Map.class);
    }

    /** {@inheritDoc} */
    @Override
    public DishCandidatePreviewResponse previewDishCandidates(Long customerId, String recordDate, String mealType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId); body.put("recordDate", recordDate); body.put("mealType", mealType);
        return post("/api/internal/agent/query/dishes/candidates", body, DishCandidatePreviewResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> dailyCustomerWorkload(String recordDate, String mealType) {
        return dailyCustomerWorkload(recordDate, mealType, List.of());
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> dailyCustomerWorkload(String recordDate, String mealType, List<String> dimensions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recordDate", recordDate); body.put("mealType", mealType); body.put("dimensions", dimensions == null ? List.of() : dimensions);
        return post("/api/internal/agent/operations/daily-customers", body, Map.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> activeCustomerSummary() {
        return post("/api/internal/agent/operations/active-customers", Map.of(), Map.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> customerProfileCount() {
        return post("/api/internal/agent/operations/customer-profiles/count", Map.of(), Map.class);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> expiringOrderSummary(String startDate, String endDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startDate", startDate); body.put("endDate", endDate);
        return post("/api/internal/agent/operations/expiring-orders", body, Map.class);
    }

    private Map<String, Object> historyBody(Long customerId, Long orderId, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("orderId", orderId);
        body.put("recentLimit", limit);
        return body;
    }

    /** 使用明确响应类型调用固定内部路径，禁止退化为 Map 响应反序列化。 */
    private <T> T post(String path, Map<String, Object> body, Class<T> responseType) {
        String context = AgentAccessContextHolder.accessContext();
        String sessionId = AgentAccessContextHolder.sessionId();
        if (context == null || sessionId == null) throw new IllegalStateException("Missing signed Agent access context");
        try {
            T response = restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .header("X-Agent-Internal-Token", internalToken).header("X-Request-Id", requestId()).header("X-Agent-Session-Id", sessionId)
                .header("X-Agent-Access-Context", context).body(body).retrieve().body(responseType);
            assertNoSensitiveResponseData(response);
            return response;
        } catch (RestClientResponseException exception) {
            throw new BusinessQueryClientException(resolveFailureCode(exception));
        } catch (ResourceAccessException exception) {
            throw new BusinessQueryClientException(isTimeout(exception) ? "TOOL_TIMEOUT" : "TOOL_UNAVAILABLE");
        }
    }

    /**
     * 在结果进入 Agent DTO 前拒绝原始手机号字段和 11 位大陆手机号，避免主系统回归时泄露到模型、日志或前端。
     * 已脱敏字段仅允许以 maskedPhone 或 maskedContactPhone 命名出现。
     *
     * @param response 主系统已反序列化的受控响应
     */
    private void assertNoSensitiveResponseData(Object response) {
        if (response == null || response instanceof String) {
            if (response instanceof String && MAINLAND_PHONE.matcher((String) response).find()) throw new BusinessQueryClientException("SENSITIVE_DATA_REJECTED");
            return;
        }
        if (containsSensitiveData(ERROR_MAPPER.valueToTree(response), null)) throw new BusinessQueryClientException("SENSITIVE_DATA_REJECTED");
    }

    /** 递归检查结构化响应，禁止仅靠 DTO 字段遗漏来隐式保护敏感数据。 */
    private boolean containsSensitiveData(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) return false;
        if (node.isTextual()) return MAINLAND_PHONE.matcher(node.asText()).find();
        if (node.isArray()) {
            for (JsonNode item : node) if (containsSensitiveData(item, fieldName)) return true;
            return false;
        }
        if (!node.isObject()) return false;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String normalized = field.getKey().toLowerCase(Locale.ROOT);
            if (("phone".equals(normalized) || "mobile".equals(normalized) || "contactphone".equals(normalized))
                && !normalized.startsWith("masked")) return true;
            if (containsSensitiveData(field.getValue(), field.getKey())) return true;
        }
        return false;
    }

    /** 仅根据异常类型判断是否为网络超时，不保留底层连接或地址信息。 */
    private boolean isTimeout(ResourceAccessException exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    /** 从主系统受控错误体读取稳定码；代理或旧响应不可解析时按 HTTP 状态安全降级。 */
    private String resolveFailureCode(RestClientResponseException exception) {
        try {
            AgentBusinessQueryErrorResponse error = ERROR_MAPPER.readValue(exception.getResponseBodyAsString(), AgentBusinessQueryErrorResponse.class);
            if (error != null && error.getCode() != null && error.getCode().matches("AGENT_QUERY_[A-Z_]+")) return error.getCode();
        } catch (Exception ignored) {
            // 禁止将不可解析的原始错误内容写入 Agent 链路。
        }
        int status = exception.getStatusCode().value();
        if (status == 401) return "AGENT_QUERY_UNAUTHORIZED";
        if (status == 403) return "AGENT_QUERY_ACCESS_DENIED";
        if (status == 404) return "AGENT_QUERY_NOT_FOUND";
        if (status >= 400 && status < 500) return "AGENT_QUERY_INVALID_REQUEST";
        return "AGENT_QUERY_INTERNAL_ERROR";
    }

    /** 仅用于解析主系统稳定错误结构，禁止保留错误 message。 */
    private static class AgentBusinessQueryErrorResponse {
        private String code;
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    private String requestId() {
        String value = MDC.get(REQUEST_ID_KEY);
        return value == null ? "" : value;
    }
}
