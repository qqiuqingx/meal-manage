package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台展示的智能排查动作草稿，不代表系统已执行写入。
 */
@Data
public class AgentDiagnosisActionDraftDto {

    private String actionCode;

    private String title;

    private String description;

    private String riskLevel;

    private String targetType;

    private String targetId;

    private Map<String, Object> beforeSnapshot = new LinkedHashMap<>();

    private Map<String, Object> afterPreview = new LinkedHashMap<>();

    private String draftDigest;

    private String snapshotDigest;

    private Long snapshotTime;

    private String requiredPermission;

    private String confirmApi;

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
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

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Map<String, Object> getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public void setBeforeSnapshot(Map<String, Object> beforeSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
    }

    public Map<String, Object> getAfterPreview() {
        return afterPreview;
    }

    public void setAfterPreview(Map<String, Object> afterPreview) {
        this.afterPreview = afterPreview;
    }

    public String getDraftDigest() {
        return draftDigest;
    }

    public void setDraftDigest(String draftDigest) {
        this.draftDigest = draftDigest;
    }

    public String getSnapshotDigest() {
        return snapshotDigest;
    }

    public void setSnapshotDigest(String snapshotDigest) {
        this.snapshotDigest = snapshotDigest;
    }

    public Long getSnapshotTime() {
        return snapshotTime;
    }

    public void setSnapshotTime(Long snapshotTime) {
        this.snapshotTime = snapshotTime;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public String getConfirmApi() {
        return confirmApi;
    }

    public void setConfirmApi(String confirmApi) {
        this.confirmApi = confirmApi;
    }
}
