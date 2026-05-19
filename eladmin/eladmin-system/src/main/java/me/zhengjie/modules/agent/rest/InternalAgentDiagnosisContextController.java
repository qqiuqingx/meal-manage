package me.zhengjie.modules.agent.rest;

import me.zhengjie.modules.agent.domain.dto.AgentCandidateDishStatsRequest;
import me.zhengjie.modules.agent.domain.dto.AgentCustomerLookupRequest;
import me.zhengjie.modules.agent.domain.dto.AgentCustomerOrdersRequest;
import me.zhengjie.modules.agent.domain.dto.AgentMealPlanLookupRequest;
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
            log.info("internal agent context request received requestId={} customerId={} customerCode={} recordDate={} mealType={}",
                    MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
            MealPlanDiagnosisContextDto context = contextService.buildContext(request);
            log.info("internal agent context request completed requestId={} customerId={} recordDate={} mealType={} costMs={}",
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
            log.info("internal agent customer profile completed requestId={} customerId={} customerCode={} present={} costMs={}",
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
            log.info("internal agent customer orders completed requestId={} customerId={} customerCode={} orders={} costMs={}",
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
            log.info("internal agent meal plan completed requestId={} recordDate={} mealType={} present={} costMs={}",
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
            log.info("internal agent candidate dish stats completed requestId={} recordDate={} stats={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), request.getRecordDate(), stats == null ? 0 : stats.size(),
                    System.currentTimeMillis() - start);
            return ResponseEntity.ok(stats);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

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

    private void bindRequestId(String requestId) {
        if (requestId != null && !requestId.trim().isEmpty()) {
            MDC.put(REQUEST_ID_KEY, requestId.trim());
        }
    }
}
