package me.zhengjie.modules.agent.security.impl;

import com.alibaba.fastjson2.JSON;
import me.zhengjie.modules.agent.security.AgentAccessContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** HMAC 访问上下文必须绑定签名、有效期、会话和请求，不能被 Agent 或前端复用。 */
class HmacAgentAccessContextServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void shouldRejectTamperedExpiredAndMismatchedContext() throws Exception {
        HmacAgentAccessContextService service = service();
        String valid = token("session-1", "request-1", Instant.now().getEpochSecond() + 60);

        assertEquals("operator", service.verify(valid, "session-1", "request-1").getOperatorName());
        assertThrows(ResponseStatusException.class, () -> service.verify(valid + "x", "session-1", "request-1"));
        assertThrows(ResponseStatusException.class, () -> service.verify(valid, "session-2", "request-1"));
        assertThrows(ResponseStatusException.class, () -> service.verify(token("session-1", "request-1", Instant.now().getEpochSecond() - 1), "session-1", "request-1"));
    }

    /** 创建服务实例并注入测试密钥，避免测试依赖环境配置。 */
    private HmacAgentAccessContextService service() {
        HmacAgentAccessContextService service = new HmacAgentAccessContextService();
        ReflectionTestUtils.setField(service, "secret", SECRET);
        return service;
    }

    /** 按生产格式生成已签名的最小访问上下文，用于验证服务端验签分支。 */
    private String token(String sessionId, String requestId, long expiresAt) throws Exception {
        AgentAccessContext context = new AgentAccessContext();
        context.setOperatorId(1L); context.setOperatorName("operator"); context.setSessionId(sessionId); context.setRequestId(requestId);
        context.setPermissions(List.of("agentDiagnosis:list")); context.setExpiresAt(expiresAt);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.toJSONBytes(context));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        return payload + "." + signature;
    }
}
