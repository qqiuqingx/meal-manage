package me.zhengjie.agent.domain.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条诊断原因，包含原因码、说明、建议和证据。
 */
public class DiagnosisReasonDto {

    private String code;
    private String title;
    private String level;
    private String confidence;
    private List<String> ruleIds = new ArrayList<>();
    private String description;
    private String suggestion;
    private List<String> nextActions = new ArrayList<>();
    private List<DiagnosisEvidenceDto> evidence = new ArrayList<>();

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public List<String> getRuleIds() {
        return ruleIds;
    }

    public void setRuleIds(List<String> ruleIds) {
        this.ruleIds = ruleIds;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public List<String> getNextActions() {
        return nextActions;
    }

    public void setNextActions(List<String> nextActions) {
        this.nextActions = nextActions;
    }

    public List<DiagnosisEvidenceDto> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<DiagnosisEvidenceDto> evidence) {
        this.evidence = evidence;
    }
}
