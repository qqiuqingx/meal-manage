package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class HttpDiagnosisToolDataClient implements DiagnosisToolDataClient {

    private static final Logger log = LoggerFactory.getLogger(HttpDiagnosisToolDataClient.class);
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String INTERNAL_TOKEN_HEADER = "X-Agent-Internal-Token";
    private static final String CUSTOMER_PROFILE_PATH = "/api/internal/agent/customer-profile";
    private static final String CUSTOMER_ORDERS_PATH = "/api/internal/agent/customer-orders";
    private static final String MEAL_PLAN_PATH = "/api/internal/agent/meal-plan";
    private static final String CANDIDATE_DISH_STATS_PATH = "/api/internal/agent/candidate-dish-stats";

    private final RestClient restClient;
    private final String internalToken;

    /**
     * 初始化工具数据客户端，并要求内部 token 必填，所有工具查询都走同一套受保护链路。
     */
    public HttpDiagnosisToolDataClient(RestClient.Builder builder,
                                       @Value("${agent.context-base-url:http://localhost:8080}") String contextBaseUrl,
                                       @Value("${agent.internal-token}") String internalToken) {
        Assert.hasText(internalToken, "agent.internal-token must be configured");
        this.restClient = builder.baseUrl(contextBaseUrl).build();
        this.internalToken = internalToken;
    }

    @Override
    public Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request) {
        return postMap(CUSTOMER_PROFILE_PATH, request, "getCustomerProfile");
    }

    @Override
    public List<Map<String, Object>> listCustomerOrders(DiagnosisToolCustomerOrdersRequest request) {
        return postList(CUSTOMER_ORDERS_PATH, request, "listCustomerOrders");
    }

    @Override
    public Map<String, Object> getMealPlan(DiagnosisToolMealPlanLookupRequest request) {
        return postMap(MEAL_PLAN_PATH, request, "getMealPlan");
    }

    @Override
    public List<Map<String, Object>> getCandidateDishStats(DiagnosisToolCandidateDishStatsRequest request) {
        return postList(CANDIDATE_DISH_STATS_PATH, request, "getCandidateDishStats");
    }

    /**
     * 查询单对象类工具数据，统一补 requestId 和内部 token，并收敛日志格式。
     */
    private Map<String, Object> postMap(String path, Object request, String toolName) {
        long start = System.currentTimeMillis();
        String requestId = requestId();
        log.info("诊断阶段 stage=工具数据查询开始 requestId={} tool={} path={}", requestId, toolName, path);
        try {
            Map<String, Object> body = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header(REQUEST_ID_HEADER, requestId)
                .header(INTERNAL_TOKEN_HEADER, internalToken)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            log.info("诊断阶段 stage=工具数据查询完成 requestId={} tool={} path={} present={} costMs={}",
                requestId, toolName, path, body != null && !body.isEmpty(), System.currentTimeMillis() - start);
            return body == null ? Collections.emptyMap() : body;
        } catch (RuntimeException ex) {
            log.warn("诊断阶段 stage=工具数据查询失败 requestId={} tool={} path={} costMs={} errorType={} errorMessage={}",
                requestId, toolName, path, System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * 查询列表类工具数据，和 postMap 共享相同的鉴权与链路日志策略。
     */
    private List<Map<String, Object>> postList(String path, Object request, String toolName) {
        long start = System.currentTimeMillis();
        String requestId = requestId();
        log.info("诊断阶段 stage=工具数据查询开始 requestId={} tool={} path={}", requestId, toolName, path);
        try {
            List<Map<String, Object>> body = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header(REQUEST_ID_HEADER, requestId)
                .header(INTERNAL_TOKEN_HEADER, internalToken)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            log.info("诊断阶段 stage=工具数据查询完成 requestId={} tool={} path={} count={} costMs={}",
                requestId, toolName, path, body == null ? 0 : body.size(), System.currentTimeMillis() - start);
            return body == null ? Collections.emptyList() : body;
        } catch (RuntimeException ex) {
            log.warn("诊断阶段 stage=工具数据查询失败 requestId={} tool={} path={} costMs={} errorType={} errorMessage={}",
                requestId, toolName, path, System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * 把外层诊断请求的 requestId 透传到工具查询，方便排查单次诊断内的所有子调用。
     */
    private String requestId() {
        String requestId = MDC.get(REQUEST_ID_KEY);
        return requestId == null ? "" : requestId;
    }
}
