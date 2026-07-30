package me.zhengjie.agent.infrastructure.llm;

import io.micrometer.observation.ObservationRegistry;
import me.zhengjie.agent.config.AgentProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.retry.support.RetryTemplate;

/** 已验收 OpenAI-compatible 代理的原生 Spring AI 适配器；未配置时不会发起网络调用。 */
@Component
public class OpenAiCompatibleProviderModelGateway implements ProviderModelGateway {
    private static final String ID = "openai-compatible";
    private final AgentProperties properties;
    private final ObjectProvider<ToolCallingManager> toolCallingManager;

    public OpenAiCompatibleProviderModelGateway(AgentProperties properties,
                                                ObjectProvider<ToolCallingManager> toolCallingManager) {
        this.properties = properties; this.toolCallingManager = toolCallingManager;
    }
    @Override public String providerId() { return ID; }
    @Override public boolean isConfigured() {
        AgentProperties.ModelProvider provider = provider();
        return provider != null && provider.isEnabled() && "OPENAI_COMPATIBLE".equals(provider.getProtocol())
            && notBlank(provider.getBaseUrl()) && notBlank(provider.getApiKey()) && notBlank(provider.getModel());
    }
    @Override public boolean supports(AgentProperties.ModelProfile profile) {
        AgentProperties.ModelProvider provider = provider();
        return isConfigured() && (!profile.isStructuredOutput() || provider.isStructuredOutput())
            && (!profile.isToolCalling() || provider.isToolCalling());
    }
    @Override public ChatClient chatClient(AgentProperties.ModelProfile profile) {
        if (!isConfigured()) throw new IllegalStateException("MODEL_PROVIDER_NOT_AVAILABLE: " + ID);
        AgentProperties.ModelProvider provider = provider();
        OpenAiApi api = OpenAiApi.builder().baseUrl(provider.getBaseUrl()).apiKey(provider.getApiKey())
            .restClientBuilder(RestClient.builder()).build();
        OpenAiChatModel model = OpenAiChatModel.builder().openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(provider.getModel()).build())
            .toolCallingManager(toolCallingManager.getIfAvailable())
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .observationRegistry(ObservationRegistry.NOOP)
            .build();
        return ChatClient.create(model);
    }
    private AgentProperties.ModelProvider provider() { return properties.getModels().getProviders().get(ID); }
    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
}
