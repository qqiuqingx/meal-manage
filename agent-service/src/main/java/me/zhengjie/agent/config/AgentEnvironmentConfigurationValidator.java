package me.zhengjie.agent.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * 校验只在受控部署环境强制启用的 Agent 安全配置。
 *
 * <p>开发和单元测试允许使用空内部令牌；生产、预发及 staging 环境必须显式配置。</p>
 */
@Component
public class AgentEnvironmentConfigurationValidator implements InitializingBean {
    private static final Set<String> SECURED_PROFILES =
        Set.of("prod", "production", "pre", "preprod", "staging");

    private final AgentProperties properties;
    private final Environment environment;

    /** 注入强类型 Agent 配置和当前 Spring 环境。 */
    public AgentEnvironmentConfigurationValidator(AgentProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /** Bean 初始化时执行环境相关安全校验，使错误在服务接流量前暴露。 */
    @Override
    public void afterPropertiesSet() {
        validate();
    }

    /** 校验受保护环境必须提供非空内部调用令牌。 */
    void validate() {
        if (!requiresInternalToken()) return;
        if (properties.getInternalToken() == null || properties.getInternalToken().trim().isEmpty()) {
            throw new IllegalStateException(
                "agent.internal-token must be configured for secured profile");
        }
    }

    /** 判断当前激活 profile 是否属于生产或预发类受保护环境。 */
    private boolean requiresInternalToken() {
        return Arrays.stream(environment.getActiveProfiles())
            .map(profile -> profile.toLowerCase(Locale.ROOT))
            .anyMatch(SECURED_PROFILES::contains);
    }
}
