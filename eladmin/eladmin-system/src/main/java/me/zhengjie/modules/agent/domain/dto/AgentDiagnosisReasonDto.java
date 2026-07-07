package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能排查原因项。
 */
@Data
public class AgentDiagnosisReasonDto {

    private String code;

    private String title;

    private String level;

    private String confidence;

    private List<String> ruleIds = new ArrayList<>();

    private String description;

    private String suggestion;

    private List<String> nextActions = new ArrayList<>();

    private List<AgentDiagnosisEvidenceDto> evidence = new ArrayList<>();
}
