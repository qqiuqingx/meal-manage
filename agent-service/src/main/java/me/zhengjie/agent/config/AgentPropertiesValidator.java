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
    }
}
