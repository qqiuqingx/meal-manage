package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 后台展示的智能排查结果。
 */
@Data
public class AgentDiagnosisResponse {

    private String requestId;

    private Long customerId;

    private String customerName;

    private String recordDate;

    private String mealType;

    private String summary;

    private String ruleVersionDigest;

    private String modelName;

    private String confidence;

    private boolean fallback;

    private String fallbackReason;

    private List<String> nextActions = new ArrayList<>();

    private List<Map<String, Object>> diagnosisTrace = new ArrayList<>();

    private List<Map<String, Object>> toolCallSummary = new ArrayList<>();

    private List<AgentDiagnosisReasonDto> reasons = new ArrayList<>();

    private List<AgentDiagnosisActionDraftDto> actionDrafts = new ArrayList<>();
}
