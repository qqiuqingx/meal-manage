package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.rest.AnonymousPostMapping;
import me.zhengjie.modules.agent.query.domain.dto.AgentDailyCustomerStatsDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationCountDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationDailyRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationOrderRequest;
import me.zhengjie.modules.agent.query.service.AgentOperationQueryService;
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

/** Agent 跨客户运营统计内部接口，仅返回授权范围内的聚合结果。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/agent/operations")
public class InternalAgentOperationQueryController {
    private static final String INTERNAL_TOKEN_HEADER = "X-Agent-Internal-Token";
    private static final String ACCESS_CONTEXT_HEADER = "X-Agent-Access-Context";
    private final AgentOperationQueryService operationQueryService;
    private final AgentAccessContextService accessContextService;
    private final AgentQueryPermissionService permissionService;
    private final AgentCustomerDataScopeResolver customerDataScopeResolver;

    @Value("${agent.internal-token}")
    private String internalToken;

    /** 查询每日已排餐、已核销、待核销和排餐失败客户聚合。 */
    @AnonymousPostMapping("/daily-customers")
    public ResponseEntity<AgentDailyCustomerStatsDto> dailyCustomers(
            @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
            @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
            @Validated @RequestBody AgentOperationDailyRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "mealPlan:list");
        return ResponseEntity.ok(operationQueryService.dailyCustomers(request));
    }

    /** 查询进行中且仍有可用餐数的客户去重数。 */
    @AnonymousPostMapping("/active-customers")
    public ResponseEntity<AgentOperationCountDto> activeCustomers(
            @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
            @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken) {
        require(agentToken, accessToken, sessionId, requestId, "customerOrder:list");
        return ResponseEntity.ok(operationQueryService.activeCustomers());
    }

    /** 查询当前客服授权数据范围内的客户档案总数。 */
    @AnonymousPostMapping("/customer-profiles/count")
    public ResponseEntity<AgentOperationCountDto> customerProfileCount(
            @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
            @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken) {
        require(agentToken, accessToken, sessionId, requestId, "customerProfile:list");
        return ResponseEntity.ok(operationQueryService.customerProfileCount());
    }

    /** 查询指定日期范围内到期的进行中订单数量。 */
    @AnonymousPostMapping("/expiring-orders")
    public ResponseEntity<AgentOperationCountDto> expiringOrders(
            @RequestHeader("X-Request-Id") String requestId, @RequestHeader("X-Agent-Session-Id") String sessionId,
            @RequestHeader(INTERNAL_TOKEN_HEADER) String agentToken, @RequestHeader(ACCESS_CONTEXT_HEADER) String accessToken,
            @RequestBody(required = false) AgentOperationOrderRequest request) {
        require(agentToken, accessToken, sessionId, requestId, "customerOrder:list");
        return ResponseEntity.ok(operationQueryService.expiringOrders(request));
    }

    private void require(String agentToken, String accessToken, String sessionId, String requestId, String... permissions) {
        if (!StringUtils.hasText(agentToken) || !StringUtils.hasText(internalToken)
            || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8), agentToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid agent internal token");
        }
        AgentAccessContext context = accessContextService.verify(accessToken, sessionId, requestId);
        permissionService.require(context, permissions);
        AgentCustomerDataScopeContext.bind(customerDataScopeResolver.resolve(context));
    }
}
