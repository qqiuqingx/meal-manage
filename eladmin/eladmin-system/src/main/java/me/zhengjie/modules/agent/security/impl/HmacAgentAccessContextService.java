package me.zhengjie.modules.agent.security.impl;

import com.alibaba.fastjson2.JSON;
import me.zhengjie.modules.agent.security.AgentAccessContext;
import me.zhengjie.modules.agent.security.AgentAccessContextService;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 使用 HMAC 签名的客服访问上下文实现，签名密钥仅保存在主系统配置中。
 */
@Service
public class HmacAgentAccessContextService implements AgentAccessContextService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${agent.access-context-secret:}")
    private String secret;

    @Value("${agent.access-context-ttl-seconds:120}")
    private long ttlSeconds;

    @PostConstruct
    void validateConfiguration() {
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new IllegalStateException("agent.access-context-secret must contain at least 32 characters");
        }
    }

    /** {@inheritDoc} */
    @Override
    public String issue(String sessionId, String requestId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(requestId)) {
            throw forbidden("Agent access context requires sessionId and requestId");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            throw forbidden("No authenticated operator for Agent access context");
        }
        AgentAccessContext context = new AgentAccessContext();
        context.setOperatorId(SecurityUtils.getCurrentUserId());
        context.setOperatorName(SecurityUtils.getCurrentUsername());
        context.setSessionId(sessionId);
        context.setRequestId(requestId);
        context.setPermissions(SecurityUtils.getCurrentUser().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(StringUtils::hasText).sorted().collect(Collectors.toList()));
        List<Long> dataScopeDeptIds = SecurityUtils.getCurrentUserDataScope();
        context.setAllDataScope(dataScopeDeptIds == null || dataScopeDeptIds.isEmpty());
        context.setDataScopeDeptIds(dataScopeDeptIds == null ? java.util.Collections.emptyList() : dataScopeDeptIds);
        context.setExpiresAt(Instant.now().getEpochSecond() + Math.max(ttlSeconds, 30));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.toJSONBytes(context));
        return payload + "." + sign(payload);
    }

    /** {@inheritDoc} */
    @Override
    public AgentAccessContext verify(String token, String sessionId, String requestId) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(sessionId) || !StringUtils.hasText(requestId)) {
            throw forbidden("Missing Agent access context");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw forbidden("Invalid Agent access context signature");
        }
        try {
            AgentAccessContext context = JSON.parseObject(Base64.getUrlDecoder().decode(parts[0]), AgentAccessContext.class);
            if (context == null || context.getExpiresAt() < Instant.now().getEpochSecond()
                    || !sessionId.equals(context.getSessionId()) || !requestId.equals(context.getRequestId())) {
                throw forbidden("Expired or mismatched Agent access context");
            }
            return context;
        } catch (IllegalArgumentException exception) {
            throw forbidden("Invalid Agent access context payload");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign Agent access context", exception);
        }
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
