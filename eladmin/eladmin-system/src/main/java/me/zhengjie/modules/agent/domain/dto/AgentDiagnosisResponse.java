package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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

    private boolean fallback;

    private List<AgentDiagnosisReasonDto> reasons = new ArrayList<>();
}
