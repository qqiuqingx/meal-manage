package me.zhengjie.modules.agent.rest;

import me.zhengjie.modules.agent.domain.dto.AgentCandidateDishStatsRequest;
import me.zhengjie.modules.agent.domain.dto.AgentCustomerLookupRequest;
import me.zhengjie.modules.agent.domain.dto.AgentCustomerOrdersRequest;
import me.zhengjie.modules.agent.domain.dto.AgentMealRefundsRequest;
import me.zhengjie.modules.agent.domain.dto.AgentMealPlanLookupRequest;
import me.zhengjie.modules.agent.domain.dto.AgentPackageSpecRequest;
import me.zhengjie.modules.agent.domain.dto.AgentVerificationLogsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.annotation.rest.AnonymousPostMapping;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.agent.service.AgentDiagnosisContextService;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import org.slf4j.MDC;
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

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * 内部诊断上下文接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/agent")
public class InternalAgentDiagnosisContextController {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final String INTERNAL_TOKEN_HEADER = "X-Agent-Internal-Token";

    private final AgentDiagnosisContextService contextService;

    @Value("${agent.internal-token}")
    private String internalToken;

    /**
     * 启动时强制要求内部调用 token 已配置，避免内部工具接口意外以空口令暴露。
     */
    @PostConstruct
    void validateInternalToken() {
        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException("agent.internal-token must be configured");
        }
    }

    @AnonymousPostMapping("/meal-plan/context")
    public ResponseEntity<MealPlanDiagnosisContextDto> buildContext(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                    @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                    @Validated @RequestBody MealPlanDiagnosisContextRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            log.info("诊断阶段 stage=内部上下文请求接收 requestId={} customerId={} customerCode={} recordDate={} mealType={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
            MealPlanDiagnosisContextDto context = contextService.buildContext(request);
            log.info("诊断阶段 stage=内部上下文请求完成 requestId={} customerId={} recordDate={} mealType={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                    System.currentTimeMillis() - start);
            return ResponseEntity.ok(context);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/customer-profile")
    public ResponseEntity<CustomerProfileDetailDto> getCustomerProfile(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                       @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                       @Validated @RequestBody AgentCustomerLookupRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            CustomerProfileDetailDto profile = contextService.resolveCustomerProfile(request.getCustomerId(), request.getCustomerCode());
            log.info("诊断阶段 stage=内部客户档案查询完成 requestId={} customerId={} customerCode={} present={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), profile != null,
                    System.currentTimeMillis() - start);
            return ResponseEntity.ok(profile);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/customer-orders")
    public ResponseEntity<List<CustomerOrderDetailDto>> listCustomerOrders(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                          @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                          @Validated @RequestBody AgentCustomerOrdersRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            List<CustomerOrderDetailDto> orders = contextService.resolveOrders(request.getCustomerId(), request.getCustomerCode(), request.getPage(), request.getSize());
            log.info("诊断阶段 stage=内部客户订单查询完成 requestId={} customerId={} customerCode={} orders={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), orders == null ? 0 : orders.size(),
                    System.currentTimeMillis() - start);
            return ResponseEntity.ok(orders);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/meal-plan")
    public ResponseEntity<MealPlanDetailVO> getMealPlan(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                        @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                        @Validated @RequestBody AgentMealPlanLookupRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            MealPlanDetailVO mealPlan = contextService.resolveMealPlan(request.getRecordDate(), request.getMealType());
            log.info("诊断阶段 stage=内部排餐查询完成 requestId={} recordDate={} mealType={} present={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getRecordDate(), request.getMealType(), mealPlan != null,
                    System.currentTimeMillis() - start);
            return ResponseEntity.ok(mealPlan);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/candidate-dish-stats")
    public ResponseEntity<List<MealPackageStatDto>> getCandidateDishStats(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                          @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                          @Validated @RequestBody AgentCandidateDishStatsRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            List<MealPackageStatDto> stats = contextService.resolveCandidateDishStats(request.getRecordDate());
            log.info("诊断阶段 stage=内部候选菜品统计查询完成 requestId={} recordDate={} stats={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getRecordDate(), stats == null ? 0 : stats.size(),
                    System.currentTimeMillis() - start);
            return ResponseEntity.ok(stats);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/customer-exclude-dates")
    public ResponseEntity<Map<String, Object>> getCustomerExcludeDates(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                       @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                       @Validated @RequestBody AgentCustomerLookupRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = contextService.resolveCustomerExcludeDates(request.getCustomerId(), request.getCustomerCode());
            log.info("诊断阶段 stage=内部客户排除日期查询完成 requestId={} customerId={} customerCode={} present={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(),
                    result != null && Boolean.TRUE.equals(result.get("present")), System.currentTimeMillis() - start);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/order-meal-balance")
    public ResponseEntity<Map<String, Object>> getOrderMealBalance(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                   @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                   @Validated @RequestBody AgentCustomerOrdersRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = contextService.resolveOrderMealBalance(
                    request.getCustomerId(), request.getCustomerCode(), request.getPage(), request.getSize());
            log.info("诊断阶段 stage=内部订单餐数余额查询完成 requestId={} customerId={} customerCode={} orderCount={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(),
                    result == null ? 0 : result.get("orderCount"), System.currentTimeMillis() - start);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/dish-candidate-detail")
    public ResponseEntity<List<Map<String, Object>>> getDishCandidateDetail(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                            @Validated @RequestBody AgentCandidateDishStatsRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            List<Map<String, Object>> result = contextService.resolveDishCandidateDetail(request.getRecordDate());
            log.info("诊断阶段 stage=内部候选菜明细查询完成 requestId={} recordDate={} count={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getRecordDate(), result == null ? 0 : result.size(),
                    System.currentTimeMillis() - start);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/package-spec")
    public ResponseEntity<Map<String, Object>> getPackageSpec(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                              @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                              @Validated @RequestBody AgentPackageSpecRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = contextService.resolvePackageSpec(
                    request.getCustomerId(), request.getCustomerCode(), request.getParentPackageId(), request.getChildPackageId());
            log.info("诊断阶段 stage=内部套餐规格查询完成 requestId={} customerId={} customerCode={} parentPackageId={} childPackageId={} present={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getParentPackageId(),
                    request.getChildPackageId(), result != null && Boolean.TRUE.equals(result.get("present")),
                    System.currentTimeMillis() - start);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/verification-logs")
    public ResponseEntity<List<Map<String, Object>>> listVerificationLogs(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                          @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                          @Validated @RequestBody AgentVerificationLogsRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            List<Map<String, Object>> result = contextService.resolveVerificationLogs(
                    request.getCustomerId(), request.getCustomerCode(), request.getOrderId(),
                    request.getRecordDateStart(), request.getRecordDateEnd(), request.getMealType());
            log.info("诊断阶段 stage=内部核销日志查询完成 requestId={} customerId={} customerCode={} orderId={} count={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getOrderId(),
                    result == null ? 0 : result.size(), System.currentTimeMillis() - start);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/meal-refunds")
    public ResponseEntity<List<Map<String, Object>>> listMealRefunds(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                     @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                     @Validated @RequestBody AgentMealRefundsRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            List<Map<String, Object>> result = contextService.resolveMealRefunds(request.getCustomerId(), request.getCustomerCode(), request.getOrderId());
            log.info("诊断阶段 stage=内部退餐日志查询完成 requestId={} customerId={} customerCode={} orderId={} count={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getOrderId(),
                    result == null ? 0 : result.size(), System.currentTimeMillis() - start);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    @AnonymousPostMapping("/meal-plan-generation-snapshot")
    public ResponseEntity<Map<String, Object>> getMealPlanGenerationSnapshot(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                                             @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String agentToken,
                                                                             @Validated @RequestBody AgentMealPlanLookupRequest request) {
        verifyInternalToken(agentToken);
        bindRequestId(requestId);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = contextService.resolveMealPlanGenerationSnapshot(request.getRecordDate(), request.getMealType());
            log.info("诊断阶段 stage=内部排餐生成快照查询完成 requestId={} recordDate={} mealType={} present={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getRecordDate(), request.getMealType(),
                    result != null && Boolean.TRUE.equals(result.get("present")), System.currentTimeMillis() - start);
            return ResponseEntity.ok(result);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    /**
     * 使用常量时间比较校验内部 token，既拦住未授权请求，也避免把 token 内容写入日志。
     */
    private void verifyInternalToken(String agentToken) {
        if (!StringUtils.hasText(agentToken) || !StringUtils.hasText(internalToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid agent internal token");
        }
        byte[] expectedBytes = internalToken.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = agentToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid agent internal token");
        }
    }

    /**
     * 把上游 requestId 绑定到 MDC，便于把 agent-service 和内部查询日志串成同一条链路。
     */
    private void bindRequestId(String requestId) {
        if (requestId != null && !requestId.trim().isEmpty()) {
            MDC.put(REQUEST_ID_KEY, requestId.trim());
        }
    }
}
