package me.zhengjie.agent.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 诊断使用的结构化业务规则。
 */
public class DiagnosisRule {

    private String ruleId;
    private String reasonCode;
    private Integer version;
    private String scene;
    private String title;
    private String description;
    private List<String> triggerConditions = new ArrayList<>();
    private List<String> requiredTools = new ArrayList<>();
    private List<String> requiredData = new ArrayList<>();
    private List<String> decisionHints = new ArrayList<>();
    private List<String> evidenceFields = new ArrayList<>();
    private List<String> nextActions = new ArrayList<>();
    private String severity;
    private String owner;

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTriggerConditions() {
        return triggerConditions;
    }

    public void setTriggerConditions(List<String> triggerConditions) {
        this.triggerConditions = triggerConditions;
    }

    public List<String> getRequiredTools() {
        return requiredTools;
    }

    public void setRequiredTools(List<String> requiredTools) {
        this.requiredTools = requiredTools;
    }

    public List<String> getRequiredData() {
        return requiredData;
    }

    public void setRequiredData(List<String> requiredData) {
        this.requiredData = requiredData;
    }

    public List<String> getDecisionHints() {
        return decisionHints;
    }

    public void setDecisionHints(List<String> decisionHints) {
        this.decisionHints = decisionHints;
    }

    public List<String> getEvidenceFields() {
        return evidenceFields;
    }

    public void setEvidenceFields(List<String> evidenceFields) {
        this.evidenceFields = evidenceFields;
    }

    public List<String> getNextActions() {
        return nextActions;
    }

    public void setNextActions(List<String> nextActions) {
        this.nextActions = nextActions;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
