package me.zhengjie.agent.infrastructure.llm;

import me.zhengjie.agent.config.AgentProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/** 供应用层使用的模型网关，隐藏 provider 选择、熔断和可恢复失败重放细节。 */
@Component
@Primary
public class FallbackAgentModelGateway implements AgentModelGateway {
    private final FallbackModelExecutor executor;
    private final AgentProperties properties;
    public FallbackAgentModelGateway(FallbackModelExecutor executor, AgentProperties properties) { this.executor = executor; this.properties = properties; }
    @Override public boolean isConfigured(String profile) { return executor.isConfigured(profile); }
    @Override public ModelProfile profile(String profile) {
        AgentProperties.ModelProfile value = executor.profile(profile);
        String id = profile == null || profile.isBlank() ? "default" : profile;
        return new ModelProfile(id, value.getModel(), value.getTimeoutMs(), value.isStructuredOutput(), value.isToolCalling(), value.getMaxRetries());
    }
    /** 兼容旧调用；新代码必须使用 execute，才能保证一个完整调用的 fallback 语义。 */
    @Override public ChatClient chatClient(String profile) { return execute(profile, Function.identity()); }
    @Override public <T> T execute(String profile, Function<ChatClient, T> invocation) { return executor.execute(profile, invocation); }
    @Override public boolean hasFallback(String profile) { return executor.hasFallback(profile); }
}
