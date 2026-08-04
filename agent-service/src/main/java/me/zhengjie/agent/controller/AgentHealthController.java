package me.zhengjie.agent.controller;

import me.zhengjie.agent.client.MainSystemQueryClient;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.domain.dto.AgentHealthResponse;
import me.zhengjie.agent.infrastructure.llm.AgentModelGateway;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.rule.RuleRegistryLoader;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final MainSystemQueryClient toolDataClient;
    private final AgentModelGateway modelGateway;
    private final AgentProperties properties;

    public AgentHealthController(RuleRegistryLoader ruleRegistryLoader,
                                 MainSystemQueryClient toolDataClient,
                                 AgentModelGateway modelGateway, AgentProperties properties) {
        this.ruleRegistryLoader = ruleRegistryLoader;
        this.toolDataClient = toolDataClient;
        this.modelGateway = modelGateway;
        this.properties = properties;
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
        response.setModelConfigured(modelGateway.isConfigured("default"));
        response.setFallbackModelConfigured(modelGateway.hasFallback("default"));
        response.setToolClientConfigured(toolDataClient != null
            && StringUtils.hasText(properties.getContextBaseUrl())
            && StringUtils.hasText(properties.getInternalToken()));
        response.setStatus(response.isRuleRegistryLoaded() ? "UP" : "DOWN");
        return response;
    }

    /** 进程存活探针：不读取规则、模型或任何远程依赖。 */
    @GetMapping("/health/liveness")
    public ResponseEntity<java.util.Map<String, String>> liveness() {
        return ResponseEntity.ok(java.util.Map.of("status", "UP"));
    }

    /** 就绪探针：规则目录未能加载时拒绝接收聊天流量，但不触发真实模型调用。 */
    @GetMapping("/health/readiness")
    public ResponseEntity<AgentHealthResponse> readiness() {
        AgentHealthResponse response = health();
        return response.isRuleRegistryLoaded() ? ResponseEntity.ok(response)
            : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /** 依赖配置摘要：只返回布尔状态和规则摘要，不暴露 token、URL 或异常原文。 */
    @GetMapping("/health/dependency")
    public AgentHealthResponse dependency() {
        return health();
    }
}
