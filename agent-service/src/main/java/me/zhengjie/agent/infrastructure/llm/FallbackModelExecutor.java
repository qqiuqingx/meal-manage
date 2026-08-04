package me.zhengjie.agent.infrastructure.llm;

import me.zhengjie.agent.config.AgentProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 创建模型执行器并注入 provider 列表与模型配置。
     *
     * @param providers 可用的模型 provider
     * @param properties Agent 服务模型配置
     */
    @Autowired
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
    /** 判断指定模型配置是否存在可用的备用 provider。 */
    public boolean hasFallback(String profileName) {
        AgentProperties.ModelProfile profile = profile(profileName);
        return providerOrder(profile).stream().skip(1).map(providers::get).anyMatch(provider -> provider != null && provider.isConfigured() && provider.supports(profile));
    }
    /** 解析模型配置名称，并对默认配置和兼容 connectivity 名称做固定映射。 */
    public AgentProperties.ModelProfile profile(String profileName) {
        String name = profileName == null || profileName.isBlank() ? "default" : profileName.trim();
        AgentProperties.ModelProfile profile = properties.getModels().getProfiles().get(name);
        if (profile == null && "connectivity".equals(name)) profile = properties.getModels().getProfiles().get("default");
        if (profile == null) throw new IllegalArgumentException("MODEL_PROFILE_NOT_AVAILABLE: " + name);
        return profile;
    }
    /** 返回 provider 健康状态跟踪器，供健康检查读取。 */
    public ProviderHealthTracker healthTracker() { return healthTracker; }

    /** 按主 provider、配置备用 provider 和全局备用顺序生成去重后的尝试序列。 */
    private List<String> providerOrder(AgentProperties.ModelProfile profile) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(profile.getProvider()); ids.addAll(profile.getFallbackProviders()); ids.addAll(properties.getModels().getFallbackOrder());
        return new ArrayList<>(ids);
    }
}
