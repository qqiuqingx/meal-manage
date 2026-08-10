package me.zhengjie.agent.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import me.zhengjie.agent.config.AgentProperties;

/** Spring AI provider 适配器；provider 专属 Bean 被限制在 infrastructure 包。 */
@Component
public class SpringAiAgentModelGateway implements ProviderModelGateway {
    private final ChatClient.Builder builder;
    private final AgentProperties properties;
    public SpringAiAgentModelGateway(ObjectProvider<ChatClient.Builder> builderProvider, AgentProperties properties) { this.builder = builderProvider.getIfAvailable(); this.properties = properties; }
    public boolean isConfigured(String profile) {
        return isConfigured() && properties.getModels().getProfiles().containsKey(normalize(profile));
    }
    public AgentModelGateway.ModelProfile profile(String profile) {
        String id = normalize(profile);
        AgentProperties.ModelProfile configured = properties.getModels().getProfiles().get(id);
        if (configured == null) throw new IllegalArgumentException("MODEL_PROFILE_NOT_AVAILABLE: " + id);
        return new AgentModelGateway.ModelProfile(id, configured.getModel(), configured.getTimeoutMs(), configured.isStructuredOutput(), configured.isToolCalling(), configured.getMaxRetries());
    }
    /**
     * 根据模型 profile 创建带有目标模型选项的 ChatClient。
     *
     * @param profile 模型 profile 名称
     * @return 使用目标模型配置的 ChatClient
     */
    public ChatClient chatClient(String profile) {
        if (builder == null) throw new IllegalStateException("MODEL_PROFILE_NOT_AVAILABLE: " + profile);
        AgentModelGateway.ModelProfile selected = profile(profile);
        return builder.clone()
            .defaultOptions(ChatOptions.builder().model(selected.model()))
            .build();
    }
    /** 保持既有 provider 适配器测试契约，同时由统一网关在生产路径执行完整调用 fallback。 */
    public AgentModelGateway.ModelProfile requireCapabilities(String profile, boolean structuredOutput, boolean toolCalling) {
        AgentModelGateway.ModelProfile selected = profile(profile);
        if (structuredOutput && !selected.structuredOutput()) throw new IllegalStateException("MODEL_CAPABILITY_UNSUPPORTED: structured-output");
        if (toolCalling && !selected.toolCalling()) throw new IllegalStateException("MODEL_CAPABILITY_UNSUPPORTED: tool-calling");
        return selected;
    }
    /** {@inheritDoc} */
    @Override public String providerId() { return "deepseek"; }
    /** {@inheritDoc} */
    @Override public boolean isConfigured() {
        AgentProperties.ModelProvider provider = properties.getModels().getProviders().get(providerId());
        return builder != null && (provider == null || provider.isEnabled());
    }
    /** {@inheritDoc} */
    @Override public boolean supports(AgentProperties.ModelProfile profile) {
        AgentProperties.ModelProvider provider = properties.getModels().getProviders().get(providerId());
        return isConfigured() && (provider == null || (!profile.isStructuredOutput() || provider.isStructuredOutput())
            && (!profile.isToolCalling() || provider.isToolCalling()));
    }
    /**
     * 根据已绑定的模型 profile 创建 DeepSeek provider 的 ChatClient。
     *
     * @param profile 已解析的模型 profile
     * @return 使用 profile 中模型名的 ChatClient
     */
    @Override public ChatClient chatClient(AgentProperties.ModelProfile profile) {
        if (builder == null) throw new IllegalStateException("MODEL_PROVIDER_NOT_AVAILABLE: " + providerId());
        String model = profile == null ? "default" : profile.getModel();
        return builder.clone().defaultOptions(ChatOptions.builder().model(model)).build();
    }
    private String normalize(String profile) {
        return profile == null || profile.isBlank() ? "default" : profile.trim();
    }
}
