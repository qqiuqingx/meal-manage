package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能排查运营看板统计结果。
 */
@Data
public class AgentOperationStatsDto {

    private Long diagnosisCount;

    private Long fallbackCount;

    private Double fallbackRate;

    private Double averageDiagnosisCostMs;

    private Long toolFailureCount;

    private Double toolFailureRate;

    private Long actionDraftCount;

    private Long actionDraftConfirmedCount;

    private Double actionDraftConfirmationRate;

    private Long feedbackCount;

    private Double feedbackAcceptedRate;

    private Map<String, Long> reasonCodeDistribution = new LinkedHashMap<>();

    private Map<String, Long> actualReasonDistribution = new LinkedHashMap<>();

    private Map<String, Long> highFrequencyUnknownReasons = new LinkedHashMap<>();
}
