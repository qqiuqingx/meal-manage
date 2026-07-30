package me.zhengjie.agent.infrastructure.llm;

import me.zhengjie.agent.config.AgentProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 以完整模型调用为原子单位执行主备切换；结构和业务校验异常绝不重放到另一个 provider。 */
@Component
public class FallbackModelExecutor {
    private final Map<String, ProviderModelGateway> providers;
    private final AgentProperties properties;
    private final ProviderHealthTracker healthTracker;
    private final ProviderCallExceptionClassifier classifier;

    public FallbackModelExecutor(List<ProviderModelGateway> providers, AgentProperties properties) {
        this(providers, properties, new ProviderHealthTracker(), new ProviderCallExceptionClassifier());
    }
    FallbackModelExecutor(List<ProviderModelGateway> providers, AgentProperties properties,
                          ProviderHealthTracker healthTracker, ProviderCallExceptionClassifier classifier) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(ProviderModelGateway::providerId, Function.identity(), (left, right) -> left));
        this.properties = properties; this.healthTracker = healthTracker; this.classifier = classifier;
    }

    /** 对当前请求构建全新的 ChatClient；可恢复失败最多让每个可用 provider 尝试一次。 */
    public <T> T execute(String profileName, Function<ChatClient, T> invocation) {
        AgentProperties.ModelProfile profile = profile(profileName);
        RuntimeException lastRecoverable = null;
        for (String providerId : providerOrder(profile)) {
            ProviderModelGateway provider = providers.get(providerId);
            if (provider == null || !provider.isConfigured() || !provider.supports(profile) || !healthTracker.allowRequest(providerId)) continue;
            try {
                T response = invocation.apply(provider.chatClient(profile));
                healthTracker.recordSuccess(providerId);
                return response;
            } catch (RuntimeException exception) {
                if (!classifier.isRecoverable(exception)) throw exception;
                healthTracker.recordRecoverableFailure(providerId);
                lastRecoverable = exception;
            }
        }
        throw new IllegalStateException("MODEL_UNAVAILABLE", lastRecoverable);
    }

    /** 返回至少一个可用 provider，供 readiness 展示备用配置缺失状态。 */
    public boolean isConfigured(String profileName) {
        AgentProperties.ModelProfile profile = profile(profileName);
        return providerOrder(profile).stream().map(providers::get).anyMatch(provider -> provider != null && provider.isConfigured() && provider.supports(profile));
    }
    public boolean hasFallback(String profileName) {
        AgentProperties.ModelProfile profile = profile(profileName);
        return providerOrder(profile).stream().skip(1).map(providers::get).anyMatch(provider -> provider != null && provider.isConfigured() && provider.supports(profile));
    }
    public AgentProperties.ModelProfile profile(String profileName) {
        String name = profileName == null || profileName.isBlank() ? "default" : profileName.trim();
        AgentProperties.ModelProfile profile = properties.getModels().getProfiles().get(name);
        if (profile == null && "connectivity".equals(name)) profile = properties.getModels().getProfiles().get("default");
        if (profile == null) throw new IllegalArgumentException("MODEL_PROFILE_NOT_AVAILABLE: " + name);
        return profile;
    }
    public ProviderHealthTracker healthTracker() { return healthTracker; }
    private List<String> providerOrder(AgentProperties.ModelProfile profile) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(profile.getProvider()); ids.addAll(profile.getFallbackProviders()); ids.addAll(properties.getModels().getFallbackOrder());
        return new ArrayList<>(ids);
    }
}
