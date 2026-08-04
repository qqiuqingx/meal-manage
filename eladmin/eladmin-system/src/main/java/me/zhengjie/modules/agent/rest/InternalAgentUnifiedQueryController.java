package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.rest.AnonymousPostMapping;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidateRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentHistoryQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentPackageDetailRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentRuleExplainRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentScheduledMenuQueryRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentDishSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentMetricQueryRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentProfileSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentServiceCustomerDetailRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentServiceCustomerSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentUnifiedQueryDto;
import me.zhengjie.modules.agent.query.domain.unified.AgentUnifiedQueryResponse;
import me.zhengjie.modules.agent.query.service.AgentUnifiedQueryService;
import me.zhengjie.modules.agent.security.AgentAccessContext;
import me.zhengjie.modules.agent.security.AgentAccessContextService;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeResolver;
import me.zhengjie.modules.agent.security.AgentQueryPermissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Agent 统一领域只读内部接口。
 *
 * <p>该 Controller 只承担服务身份、签名上下文、业务权限和客户数据范围校验，具体查询由统一查询服务完成。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/agent/query")
public class InternalAgentUnifiedQueryController {
    private static final String INTERNAL_TOKEN_HEADER = "X-Agent-Internal-Token";
    private static final String ACCESS_CONTEXT_HEADER = "X-Agent-Access-Context";
    private final AgentUnifiedQueryService unifiedQueryService;
    private final AgentAccessContextService accessContextService;
    private final AgentQueryPermissionService permissionService;
    private final AgentCustomerDataScopeResolver customerDataScopeResolver;

    @Value("${agent.internal-token}")
    private String internalToken;

    /** 查询客户档案，包括尚未下单客户。 */
    @AnonymousPostMapping("/customer-profiles/search")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ProfileItem>> searchCustomerProfiles(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentProfileSearchRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "customerProfile:list");
        return ResponseEntity.ok(unifiedQueryService.searchCustomerProfiles(request));
    }

    /** 查询以订单为根的服务客户列表，每笔订单独立展示。 */
    @AnonymousPostMapping("/service-customers/search")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ServiceCustomerItem>> searchServiceCustomers(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentServiceCustomerSearchRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "customerOrder:list");
        return ResponseEntity.ok(unifiedQueryService.searchServiceCustomers(request));
    }

    /** 查询单客户或单订单综合快照。 */
    @AnonymousPostMapping("/service-customers/detail")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ServiceCustomerDetailItem>> getServiceCustomerDetail(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentServiceCustomerDetailRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "customerProfile:list", "customerOrder:list");
        return ResponseEntity.ok(unifiedQueryService.getServiceCustomerDetail(request));
    }

    /** 查询排餐及限量菜品明细。 */
    @AnonymousPostMapping("/meal-plans/list")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.MealPlanItem>> listMealPlans(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentMealPlanQueryRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "mealPlan:list");
        return ResponseEntity.ok(unifiedQueryService.listMealPlans(request));
    }

    /** 查询未删除核销记录，广域查询的日期和分页边界由 Agent 与主系统共同校验。 */
    @AnonymousPostMapping("/verifications/list")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.VerificationItem>> listVerifications(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentHistoryQueryRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "mealVerification:list");
        return ResponseEntity.ok(unifiedQueryService.listVerifications(request));
    }

    /** 查询退餐记录，不返回退款金额。 */
    @AnonymousPostMapping("/refunds/list")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.RefundItem>> listRefunds(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentHistoryQueryRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "mealRefund:list");
        return ResponseEntity.ok(unifiedQueryService.listRefunds(request));
    }

    /** 预览客户指定日期餐次的候选菜及过滤原因，不写入排餐。 */
    @AnonymousPostMapping("/dishes/candidates")
    public ResponseEntity<AgentUnifiedQueryResponse<Map<String, Object>>> previewDishCandidates(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentDishCandidateRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "customerProfile:list", "customerOrder:list", "package:list", "dish:list");
        return ResponseEntity.ok(unifiedQueryService.previewDishCandidates(request));
    }

    /** 查询指定日期午餐或晚餐的公共排期菜单。 */
    @AnonymousPostMapping("/dishes/scheduled")
    public ResponseEntity<AgentUnifiedQueryResponse<Map<String, Object>>> listScheduledDishes(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentScheduledMenuQueryRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "mealPlan:list", "dish:list");
        return ResponseEntity.ok(unifiedQueryService.listScheduledDishes(request));
    }

    /** 按受控名称、类型和启用状态分页搜索菜品。 */
    @AnonymousPostMapping("/dishes/search")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.DishItem>> searchDishes(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentDishSearchRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "dish:list");
        return ResponseEntity.ok(unifiedQueryService.searchDishes(request));
    }

    /** 查询父套餐及子套餐餐品规格。 */
    @AnonymousPostMapping("/packages/detail")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.PackageDetailItem>> getPackageDetail(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentPackageDetailRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "package:list");
        return ResponseEntity.ok(unifiedQueryService.getPackageDetail(request));
    }

    /** 查询登记运营指标，指标与维度均在主系统内固定映射。 */
    @AnonymousPostMapping("/metrics/query")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.MetricItem>> queryBusinessMetrics(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentMetricQueryRequest request) {
        require(agentToken, accessToken, sessionId, requestId, metricPermission(request));
        return ResponseEntity.ok(unifiedQueryService.queryBusinessMetrics(request));
    }

    /** 查询版本化业务规则目录，不允许提交任意文档路径。 */
    @AnonymousPostMapping("/rules/explain")
    public ResponseEntity<AgentUnifiedQueryResponse<AgentUnifiedQueryDto.RuleItem>> explainBusinessRule(
        @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
        @Validated @RequestBody AgentRuleExplainRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "agentDiagnosis:list");
        return ResponseEntity.ok(unifiedQueryService.explainBusinessRule(request));
    }

    /** 按指标枚举选择最小业务权限，避免运营统计接口获得不必要的跨域读取权限。 */
    private String metricPermission(AgentMetricQueryRequest request) {
        String metric = request == null || request.getMetric() == null ? "" : request.getMetric().trim().toUpperCase(java.util.Locale.ROOT);
        if ("CUSTOMER_PROFILE_COUNT".equals(metric)) return "customerProfile:list";
        if ("ACTIVE_SERVICE_CUSTOMER_COUNT".equals(metric) || "ACTIVE_ORDER_COUNT".equals(metric) || "EXPIRING_ORDER_COUNT".equals(metric)) return "customerOrder:list";
        return "mealPlan:list";
    }

    /** 校验内部服务身份、签名访问上下文、业务权限并绑定客户数据范围。 */
    private void require(String agentToken, String accessToken, String sessionId, String requestId, String... permissions) {
        verifyInternalToken(agentToken);
        AgentAccessContext context = accessContextService.verify(accessToken, sessionId, requestId);
        permissionService.require(context, permissions);
        AgentCustomerDataScopeContext.bind(customerDataScopeResolver.resolve(context));
        if (StringUtils.hasText(requestId)) MDC.put("requestId", requestId.trim());
        if (StringUtils.hasText(sessionId)) MDC.put("sessionId", sessionId.trim());
    }

    /** 使用常量时间比较内部服务令牌。 */
    private void verifyInternalToken(String agentToken) {
        if (!StringUtils.hasText(agentToken) || !StringUtils.hasText(internalToken)
            || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8), agentToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid agent internal token");
        }
    }
}
