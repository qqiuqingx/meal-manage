package me.zhengjie.agent.infrastructure.llm;

import io.micrometer.observation.ObservationRegistry;
import me.zhengjie.agent.config.AgentProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/** 已验收 OpenAI-compatible 代理的原生 Spring AI 适配器；未配置时不会发起网络调用。 */
@Component
public class OpenAiCompatibleProviderModelGateway implements ProviderModelGateway {
    private static final String ID = "openai-compatible";
    private final AgentProperties properties;

    /**
     * 创建 OpenAI-compatible provider 适配器。
     *
     * @param properties Agent 服务模型配置
     */
    public OpenAiCompatibleProviderModelGateway(AgentProperties properties) {
        this.properties = properties;
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
    /**
     * 为一次请求创建独立的 OpenAI-compatible ChatClient，不在构造阶段发起网络调用。
     *
     * @param profile 当前请求的模型 profile；provider 模型名由 provider 配置统一决定
     * @return 使用 provider 连接参数的 ChatClient
     */
    @Override public ChatClient chatClient(AgentProperties.ModelProfile profile) {
        if (!isConfigured()) throw new IllegalStateException("MODEL_PROVIDER_NOT_AVAILABLE: " + ID);
        return ChatClient.create(createModel());
    }

    /**
     * 使用 Spring AI 2.0 的 OpenAiChatOptions 构造底层模型。
     *
     * <p>工具循环由业务层的 ToolCallAdvisor 统一负责，模型 SDK 不再注入额外的工具管理器或隐式重试。</p>
     *
     * @return 已配置连接地址、密钥和模型名的 OpenAiChatModel
     */
    OpenAiChatModel createModel() {
        AgentProperties.ModelProvider provider = provider();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .baseUrl(provider.getBaseUrl())
            .apiKey(provider.getApiKey())
            .model(provider.getModel())
            .build();
        return OpenAiChatModel.builder()
            .options(options)
            .observationRegistry(ObservationRegistry.NOOP)
            .build();
    }
    private AgentProperties.ModelProvider provider() { return properties.getModels().getProviders().get(ID); }
    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
}
