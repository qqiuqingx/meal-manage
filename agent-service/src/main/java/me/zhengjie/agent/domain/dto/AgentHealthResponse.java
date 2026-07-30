package me.zhengjie.agent.domain.dto;

/**
 * agent-service 轻量健康检查结果。
 */
public class AgentHealthResponse {

    private String status;
    private boolean ruleRegistryLoaded;
    private String ruleVersionDigest;
    private boolean modelConfigured;
    /** 是否启用至少一个模型备用 provider；false 不影响单 provider 可用性。 */
    private boolean fallbackModelConfigured;
    private boolean toolClientConfigured;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isRuleRegistryLoaded() {
        return ruleRegistryLoaded;
    }

    public void setRuleRegistryLoaded(boolean ruleRegistryLoaded) {
        this.ruleRegistryLoaded = ruleRegistryLoaded;
    }

    public String getRuleVersionDigest() {
        return ruleVersionDigest;
    }

    public void setRuleVersionDigest(String ruleVersionDigest) {
        this.ruleVersionDigest = ruleVersionDigest;
    }

    public boolean isModelConfigured() {
        return modelConfigured;
    }

    public void setModelConfigured(boolean modelConfigured) {
        this.modelConfigured = modelConfigured;
    }
    public boolean isFallbackModelConfigured() { return fallbackModelConfigured; }
    public void setFallbackModelConfigured(boolean value) { fallbackModelConfigured = value; }

    public boolean isToolClientConfigured() {
        return toolClientConfigured;
    }

    public void setToolClientConfigured(boolean toolClientConfigured) {
        this.toolClientConfigured = toolClientConfigured;
    }
}
