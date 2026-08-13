package me.zhengjie.modules.agent.formdraft.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.rest.AnonymousPostMapping;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSaveRequest;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSaveResult;
import me.zhengjie.modules.agent.formdraft.service.AgentFormDraftService;
import me.zhengjie.modules.agent.security.AgentAccessContext;
import me.zhengjie.modules.agent.security.AgentAccessContextService;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeResolver;
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

/** Agent 服务保存表单草稿的唯一内部入口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/agent/form-drafts")
public class InternalAgentFormDraftController {
    private final AgentFormDraftService draftService;
    private final AgentAccessContextService accessContextService;
    private final AgentCustomerDataScopeResolver dataScopeResolver;

    @Value("${agent.internal-token}")
    private String internalToken;

    /** 校验服务身份及短期客服上下文后创建或修订草稿。 */
    @AnonymousPostMapping(":save")
    public ResponseEntity<AgentFormDraftSaveResult> save(
        @RequestHeader("X-Request-Id") String requestId,
        @RequestHeader("X-Agent-Session-Id") String sessionId,
        @RequestHeader("X-Agent-Internal-Token") String agentToken,
        @RequestHeader("X-Agent-Access-Context") String accessToken,
        @Validated @RequestBody AgentFormDraftSaveRequest request) {
        verifyInternalToken(agentToken);
        AgentAccessContext context = accessContextService.verify(accessToken, sessionId, requestId);
        AgentCustomerDataScopeContext.bind(dataScopeResolver.resolve(context));
        return ResponseEntity.ok(draftService.save(request, context));
    }

    /** 使用常量时间比较内部服务令牌。 */
    private void verifyInternalToken(String agentToken) {
        if (!StringUtils.hasText(agentToken) || !StringUtils.hasText(internalToken)
            || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8), agentToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid agent internal token");
        }
    }
}
