package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightMealRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightOrderRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightVerificationRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealRefundsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolPackageSpecRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolVerificationLogsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import me.zhengjie.agent.security.AgentAccessContextHolder;

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
    private static final String CUSTOMER_EXCLUDE_DATES_PATH = "/api/internal/agent/customer-exclude-dates";
    private static final String ORDER_MEAL_BALANCE_PATH = "/api/internal/agent/order-meal-balance";
    private static final String PACKAGE_SPEC_PATH = "/api/internal/agent/package-spec";
    private static final String DISH_CANDIDATE_DETAIL_PATH = "/api/internal/agent/dish-candidate-detail";
    private static final String VERIFICATION_LOGS_PATH = "/api/internal/agent/verification-logs";
    private static final String MEAL_REFUNDS_PATH = "/api/internal/agent/meal-refunds";
    private static final String MEAL_PLAN_GENERATION_SNAPSHOT_PATH = "/api/internal/agent/meal-plan-generation-snapshot";
    private static final String CUSTOMER_MEAL_SUMMARY_PATH = "/api/internal/agent/customer-insight/meal-summary";
    private static final String CUSTOMER_VERIFICATION_SUMMARY_PATH = "/api/internal/agent/customer-insight/verification-summary";
    private static final String CUSTOMER_ORDER_SUMMARY_PATH = "/api/internal/agent/customer-insight/order-summary";

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

    @Override
    public Map<String, Object> getCustomerExcludeDates(DiagnosisToolCustomerLookupRequest request) {
        return postMap(CUSTOMER_EXCLUDE_DATES_PATH, request, "getCustomerExcludeDates");
    }

    @Override
    public Map<String, Object> getOrderMealBalance(DiagnosisToolCustomerOrdersRequest request) {
        return postMap(ORDER_MEAL_BALANCE_PATH, request, "getOrderMealBalance");
    }

    @Override
    public Map<String, Object> getPackageSpec(DiagnosisToolPackageSpecRequest request) {
        return postMap(PACKAGE_SPEC_PATH, request, "getPackageSpec");
    }

    @Override
    public List<Map<String, Object>> getDishCandidateDetail(DiagnosisToolCandidateDishStatsRequest request) {
        return postList(DISH_CANDIDATE_DETAIL_PATH, request, "getDishCandidateDetail");
    }

    @Override
    public List<Map<String, Object>> listVerificationLogs(DiagnosisToolVerificationLogsRequest request) {
        return postList(VERIFICATION_LOGS_PATH, request, "listVerificationLogs");
    }

    @Override
    public List<Map<String, Object>> listMealRefunds(DiagnosisToolMealRefundsRequest request) {
        return postList(MEAL_REFUNDS_PATH, request, "listMealRefunds");
    }

    @Override
    public Map<String, Object> getMealPlanGenerationSnapshot(DiagnosisToolMealPlanLookupRequest request) {
        return postMap(MEAL_PLAN_GENERATION_SNAPSHOT_PATH, request, "getMealPlanGenerationSnapshot");
    }

    @Override
    public Map<String, Object> getCustomerMealSummary(DiagnosisToolCustomerInsightMealRequest request) {
        return postMap(CUSTOMER_MEAL_SUMMARY_PATH, request, "getCustomerMealSummary");
    }

    @Override
    public Map<String, Object> getCustomerVerificationSummary(DiagnosisToolCustomerInsightVerificationRequest request) {
        return postMap(CUSTOMER_VERIFICATION_SUMMARY_PATH, request, "getCustomerVerificationSummary");
    }

    @Override
    public Map<String, Object> getCustomerOrderSummary(DiagnosisToolCustomerInsightOrderRequest request) {
        return postMap(CUSTOMER_ORDER_SUMMARY_PATH, request, "getCustomerOrderSummary");
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
                .headers(this::appendAccessContext)
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
                .headers(this::appendAccessContext)
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

    /**
     * 仅原样透传主系统签发的上下文，agent-service 不解析、不扩展其中的权限信息。
     */
    private void appendAccessContext(org.springframework.http.HttpHeaders headers) {
        String accessContext = AgentAccessContextHolder.accessContext();
        String sessionId = AgentAccessContextHolder.sessionId();
        if (accessContext != null) headers.set("X-Agent-Access-Context", accessContext);
        if (sessionId != null) headers.set("X-Agent-Session-Id", sessionId);
    }
}
