package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.rest.AnonymousPostMapping;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerOverviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerResolveRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderDetailRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderListRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerCandidateDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentHistoryQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentVerificationLogDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentRefundLogDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentPackageSpecDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentPackageDetailRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentBusinessRuleDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentRuleExplainRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishListRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidateRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidatePreviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishSummaryDto;
import me.zhengjie.modules.agent.query.service.AgentCustomerQueryService;
import me.zhengjie.modules.agent.query.service.AgentOrderQueryService;
import me.zhengjie.modules.agent.query.service.AgentHistoryQueryService;
import me.zhengjie.modules.agent.query.service.AgentMealPlanQueryService;
import me.zhengjie.modules.agent.query.service.AgentPackageQueryService;
import me.zhengjie.modules.agent.query.service.AgentBusinessRuleQueryService;
import me.zhengjie.modules.agent.query.service.AgentDishQueryService;
import me.zhengjie.modules.agent.rest.exception.AgentBusinessQueryNotFoundException;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Agent 通用业务只读内部接口。每次调用同时校验服务身份和客服访问上下文。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/agent/query")
public class InternalAgentBusinessQueryController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Agent-Internal-Token";
    private static final String ACCESS_CONTEXT_HEADER = "X-Agent-Access-Context";

    private final AgentCustomerQueryService customerQueryService;
    private final AgentOrderQueryService orderQueryService;
    private final AgentHistoryQueryService historyQueryService;
    private final AgentMealPlanQueryService mealPlanQueryService;
    private final AgentPackageQueryService packageQueryService;
    private final AgentBusinessRuleQueryService businessRuleQueryService;
    private final AgentDishQueryService dishQueryService;
    private final AgentAccessContextService accessContextService;
    private final AgentQueryPermissionService permissionService;
    private final AgentCustomerDataScopeResolver customerDataScopeResolver;

    @Value("${agent.internal-token}")
    private String internalToken;

    /** 查询客户 ID、编号或姓名候选。 */
    @AnonymousPostMapping("/customer/resolve")
    public ResponseEntity<AgentListResultDto<AgentCustomerCandidateDto>> resolveCustomer(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentCustomerResolveRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "customerProfile:list");
        return ResponseEntity.ok(customerQueryService.resolve(request.getCustomerId(), request.getCustomerCode(), request.getCustomerName()));
    }

    /** 查询客户综合概览。 */
    @AnonymousPostMapping("/customer/overview")
    public ResponseEntity<AgentCustomerOverviewDto> customerOverview(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentCustomerResolveRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "customerProfile:list", "customerOrder:list");
        return ResponseEntity.ok(customerQueryService.getOverview(request.getCustomerId(), request.getCustomerCode()));
    }

    /** 查询客户订单分页摘要。 */
    @AnonymousPostMapping("/orders/list")
    public ResponseEntity<AgentListResultDto<AgentOrderSummaryDto>> listOrders(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentOrderListRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "customerOrder:list");
        return ResponseEntity.ok(orderQueryService.listByCustomer(request.getCustomerId(), request.getStatus(),
                request.getPage() == null ? 1 : request.getPage(), request.getSize() == null ? 10 : request.getSize()));
    }

    /** 查询单笔订单，并校验当前客户上下文关系。 */
    @AnonymousPostMapping("/orders/detail")
    public ResponseEntity<AgentOrderSummaryDto> orderDetail(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentOrderDetailRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "customerOrder:list");
        AgentOrderSummaryDto result = orderQueryService.getDetail(request.getOrderId(), request.getOrderCode(), request.getCustomerId());
        if (result == null) throw new AgentBusinessQueryNotFoundException();
        return ResponseEntity.ok(result);
    }

    /** 查询客户或订单的未删除核销记录。 */
    @AnonymousPostMapping("/verifications/list")
    public ResponseEntity<AgentListResultDto<AgentVerificationLogDto>> listVerifications(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentHistoryQueryRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "mealPlan:list");
        return ResponseEntity.ok(historyQueryService.listVerifications(request));
    }

    /** 查询客户或订单的退餐记录，响应不包含退款金额。 */
    @AnonymousPostMapping("/refunds/list")
    public ResponseEntity<AgentListResultDto<AgentRefundLogDto>> listRefunds(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentHistoryQueryRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "customerOrder:list", "mealPlan:list");
        return ResponseEntity.ok(historyQueryService.listRefunds(request));
    }

    /** 查询客户指定日期和餐次的排餐及菜品明细。 */
    @AnonymousPostMapping("/meal-plans/list")
    public ResponseEntity<AgentListResultDto<AgentMealPlanSummaryDto>> listMealPlans(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentMealPlanQueryRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "mealPlan:list");
        return ResponseEntity.ok(mealPlanQueryService.query(request));
    }

    /** 查询父套餐、关联子套餐和餐品规格。 */
    @AnonymousPostMapping("/packages/detail")
    public ResponseEntity<AgentPackageSpecDto> packageDetail(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentPackageDetailRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "package:list");
        return ResponseEntity.ok(packageQueryService.getDetail(request.getParentPackageId()));
    }

    /** 查询指定菜品的类型和限量配料摘要。 */
    @AnonymousPostMapping("/dishes/list")
    public ResponseEntity<AgentListResultDto<AgentDishSummaryDto>> listDishes(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentDishListRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "dish:list");
        return ResponseEntity.ok(dishQueryService.listByIds(request.getDishIds()));
    }

    /** 查询指定日期、餐次的公共排期菜单，不返回客户、订单或地址信息。 */
    @AnonymousPostMapping("/dishes/scheduled")
    public ResponseEntity<AgentListResultDto<AgentDishSummaryDto>> listScheduledDishes(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentMealPlanQueryRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "mealPlan:list", "dish:list");
        return ResponseEntity.ok(dishQueryService.listScheduled(request.getRecordDate(), request.getMealType()));
    }

    /** 预览客户指定日期餐次的排期候选菜及套餐、过敏和忌口过滤摘要。 */
    @AnonymousPostMapping("/dishes/candidates")
    public ResponseEntity<AgentDishCandidatePreviewDto> previewDishCandidates(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentDishCandidateRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId, "customerProfile:list", "customerOrder:list", "package:list", "dish:list");
        return ResponseEntity.ok(dishQueryService.previewCandidates(request.getCustomerId(), request.getRecordDate(), request.getMealType()));
    }

    /** 查询版本化业务规则说明。 */
    @AnonymousPostMapping("/rules/explain")
    public ResponseEntity<AgentBusinessRuleDto> explainRule(
            @RequestHeader(value = "X-Request-Id") String requestId,
            @RequestHeader(value = "X-Agent-Session-Id") String sessionId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER) String agentToken,
            @RequestHeader(value = ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentRuleExplainRequest request) {
        requirePermission(agentToken, accessToken, sessionId, requestId);
        return ResponseEntity.ok(businessRuleQueryService.explain(request.getTopic()));
    }

    private void requirePermission(String agentToken, String accessToken, String sessionId, String requestId, String... permissions) {
        verifyInternalToken(agentToken);
        AgentAccessContext context = accessContextService.verify(accessToken, sessionId, requestId);
        permissionService.require(context, permissions);
        AgentCustomerDataScopeContext.bind(customerDataScopeResolver.resolve(context));
    }

    private void verifyInternalToken(String agentToken) {
        if (!StringUtils.hasText(agentToken) || !StringUtils.hasText(internalToken)
                || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8), agentToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid agent internal token");
        }
    }
}
