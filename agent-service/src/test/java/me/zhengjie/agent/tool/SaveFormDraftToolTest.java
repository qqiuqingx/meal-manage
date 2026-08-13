package me.zhengjie.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.client.MainSystemFormDraftClient;
import me.zhengjie.agent.client.MainSystemQueryClient;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.guardrail.FormDraftSensitiveDataPolicy;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.guardrail.ToolInputGuardrail;
import me.zhengjie.agent.guardrail.ToolOutputGuardrail;
import me.zhengjie.agent.tool.output.FormDraftToolOutput;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** saveFormDraft 工具副作用、预算和非缓存语义测试。 */
class SaveFormDraftToolTest {

    /** 相同参数调用两次都应到达主系统，Agent 内存缓存命中数保持为零。 */
    @Test
    void shouldNeverCacheFormDraftWrites() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AtomicInteger calls = new AtomicInteger();
        FormDraftToolOutput output = new FormDraftToolOutput();
        output.setSuccess(true);
        output.setDraftId("afd_1234567890abcdef");
        output.setDraftType("CREATE_CUSTOMER_WITH_ORDER");
        output.setStatus("READY");
        output.setRevision(1);
        output.setExpiresAt("2026-08-13 12:00:00");
        MainSystemFormDraftClient client = input -> { calls.incrementAndGet(); return output; };
        MainSystemQueryClient queryClient = (MainSystemQueryClient) Proxy.newProxyInstance(
            MainSystemQueryClient.class.getClassLoader(), new Class<?>[]{MainSystemQueryClient.class},
            (proxy, method, args) -> { throw new AssertionError("read-only client must not be invoked"); });
        SensitiveDataPolicy policy = new SensitiveDataPolicy();
        BusinessAgentTools tools = new BusinessAgentTools(new ToolRegistry(), queryClient, client,
            new ToolInputGuardrail(mapper, policy, new FormDraftSensitiveDataPolicy()),
            new ToolOutputGuardrail(mapper, policy), mapper, new AgentProperties());
        ToolExecutionContext context = new ToolExecutionContext(mapper, 6, 100);
        ToolCallback callback = tools.callbacksFor(Set.of(ToolRegistry.SAVE_FORM_DRAFT), context).get(0);
        String input = "{\"draftType\":\"CREATE_CUSTOMER_WITH_ORDER\",\"schemaVersion\":\"v1\","
            + "\"customerWithOrderPayload\":{\"customer\":{},\"order\":{}}}";

        callback.call(input);
        callback.call(input);

        assertEquals(2, calls.get());
        assertEquals(0, context.cacheHits());
        assertNull(context.cached(context.cacheKey(ToolRegistry.SAVE_FORM_DRAFT, input)));
        assertEquals(2, context.toolCalls());
    }
}
