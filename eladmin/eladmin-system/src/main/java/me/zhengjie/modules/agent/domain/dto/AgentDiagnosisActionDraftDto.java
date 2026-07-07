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

    private String requiredPermission;

    private String confirmApi;
}
