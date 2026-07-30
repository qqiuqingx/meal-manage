package me.zhengjie.agent.infrastructure.llm;

import me.zhengjie.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** provider 网络失败仅重放一次到备用 provider；非恢复失败不切换。 */
class FallbackModelExecutorTest {
    @Test
    void shouldReplayRecoverableFailureOnceToFallbackProvider() {
        AgentProperties properties = new AgentProperties();
        AgentProperties.ModelProfile profile = new AgentProperties.ModelProfile();
        profile.setProvider("deepseek"); profile.setFallbackProviders(List.of("openai-compatible"));
        properties.getModels().setProfiles(java.util.Map.of("default", profile));
        FallbackModelExecutor executor = new FallbackModelExecutor(List.of(new StubProvider("deepseek"), new StubProvider("openai-compatible")),
            properties, new ProviderHealthTracker(), new ProviderCallExceptionClassifier());
        int[] attempts = {0};
        String result = executor.execute("default", client -> ++attempts[0] == 1 ? failTimeout() : "fallback-ok");
        assertEquals("fallback-ok", result); assertEquals(2, attempts[0]);
    }
    private static String failTimeout() { throw new RuntimeException("timeout"); }
    private static final class StubProvider implements ProviderModelGateway {
        private final String id; StubProvider(String id) { this.id = id; }
        public String providerId() { return id; } public boolean isConfigured() { return true; }
        public boolean supports(AgentProperties.ModelProfile profile) { return true; }
        public ChatClient chatClient(AgentProperties.ModelProfile profile) { return null; }
    }
}
