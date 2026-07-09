package me.zhengjie.agent.controller;

import me.zhengjie.agent.client.HttpDiagnosisToolDataClient;
import me.zhengjie.agent.client.SpringAiDiagnosisAiClient;
import me.zhengjie.agent.domain.dto.AgentHealthResponse;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.rule.RuleRegistryLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent-service 健康检查接口。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentHealthController {

    private final RuleRegistryLoader ruleRegistryLoader;
    private final SpringAiDiagnosisAiClient springAiDiagnosisAiClient;
    private final HttpDiagnosisToolDataClient toolDataClient;
    private final String contextBaseUrl;
    private final String internalToken;

    public AgentHealthController(RuleRegistryLoader ruleRegistryLoader,
                                 @Nullable SpringAiDiagnosisAiClient springAiDiagnosisAiClient,
                                 @Nullable HttpDiagnosisToolDataClient toolDataClient,
                                 @Value("${agent.context-base-url:}") String contextBaseUrl,
                                 @Value("${agent.internal-token:}") String internalToken) {
        this.ruleRegistryLoader = ruleRegistryLoader;
        this.springAiDiagnosisAiClient = springAiDiagnosisAiClient;
        this.toolDataClient = toolDataClient;
        this.contextBaseUrl = contextBaseUrl;
        this.internalToken = internalToken;
    }

    /**
     * 返回规则加载和关键客户端配置状态，不触发模型或工具远程调用。
     */
    @GetMapping("/health")
    public AgentHealthResponse health() {
        AgentHealthResponse response = new AgentHealthResponse();
        RuleRegistry registry = null;
        try {
            registry = ruleRegistryLoader.load("meal-plan");
            response.setRuleRegistryLoaded(registry != null && registry.getRules() != null && !registry.getRules().isEmpty());
            response.setRuleVersionDigest(registry == null ? null : registry.getVersionDigest());
        } catch (RuntimeException ex) {
            response.setRuleRegistryLoaded(false);
            response.setRuleVersionDigest(null);
        }
        response.setModelConfigured(springAiDiagnosisAiClient != null);
        response.setToolClientConfigured(toolDataClient != null
            && StringUtils.hasText(contextBaseUrl)
            && StringUtils.hasText(internalToken));
        response.setStatus(response.isRuleRegistryLoaded() ? "UP" : "DOWN");
        return response;
    }
}
