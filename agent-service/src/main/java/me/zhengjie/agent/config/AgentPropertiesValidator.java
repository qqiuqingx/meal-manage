package me.zhengjie.agent.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 在应用开始接流量前检查无法由 Bean Validation 表达的 Agent 配置约束。 */
@Component
public class AgentPropertiesValidator implements SmartInitializingSingleton {

    private final AgentProperties properties;

    public AgentPropertiesValidator(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验规则场景映射，避免运行时因空目录或空场景键导致静默回退。
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (properties.getRules() == null || properties.getRules().getSceneDirectories() == null) {
            throw new IllegalStateException("agent.rules.scene-directories must not be null");
        }
        properties.getRules().getSceneDirectories().forEach((scene, directory) -> {
            if (!StringUtils.hasText(scene) || !StringUtils.hasText(directory)) {
                throw new IllegalStateException("agent.rules.scene-directories must contain non-blank scene and directory");
            }
        });
        properties.getModels().getProviders().forEach((id, provider) -> {
            if (provider == null || !provider.isEnabled()) return;
            if (!StringUtils.hasText(id) || !StringUtils.hasText(provider.getProtocol())) {
                throw new IllegalStateException("enabled agent model provider must have id and protocol");
            }
            if ("OPENAI_COMPATIBLE".equals(provider.getProtocol())
                && (!StringUtils.hasText(provider.getBaseUrl()) || !StringUtils.hasText(provider.getApiKey()) || !StringUtils.hasText(provider.getModel()))) {
                throw new IllegalStateException("enabled OPENAI_COMPATIBLE provider requires base-url, api-key and model");
            }
        });
        AgentProperties.ModelProfile presentation = properties.getModels().getProfiles().get("presentation");
        if (presentation != null) {
            if (!presentation.isStructuredOutput()) {
                throw new IllegalStateException("presentation profile requires structured-output");
            }
            if (presentation.isToolCalling()) {
                throw new IllegalStateException("presentation profile must disable tool-calling");
            }
            if (presentation.getTimeoutMs() > 3000) {
                throw new IllegalStateException("presentation profile timeout must be <= 3000ms");
            }
            if (presentation.getMaxRetries() > 1) {
                throw new IllegalStateException("presentation profile max-retries must be <= 1");
            }
        }
    }
}
