package me.zhengjie.agent.domain.dto;

import java.util.ArrayList;
import java.util.List;

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
    /** 当前系统展示规则数量，不包含字段目录详情。 */
    private int presentationRuleCount;
    /** 展示规则健康告警码，不包含字段详情或业务值。 */
    private List<String> presentationWarnings = new ArrayList<>();
    /** 工具登记表中的只读工具数量；不代表当前客服本轮白名单数量。 */
    private int readOnlyToolCount;
    /** 是否登记了唯一受控草稿写工具；是否可用由主系统开关和权限决定。 */
    private boolean formDraftWriteToolRegistered;

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

    public int getPresentationRuleCount() { return presentationRuleCount; }
    public void setPresentationRuleCount(int value) { presentationRuleCount = value; }
    public List<String> getPresentationWarnings() { return presentationWarnings; }
    public void setPresentationWarnings(List<String> value) {
        presentationWarnings = value == null ? new ArrayList<>() : value;
    }

    public int getReadOnlyToolCount() { return readOnlyToolCount; }
    public void setReadOnlyToolCount(int value) { readOnlyToolCount = value; }
    public boolean isFormDraftWriteToolRegistered() { return formDraftWriteToolRegistered; }
    public void setFormDraftWriteToolRegistered(boolean value) { formDraftWriteToolRegistered = value; }
}
