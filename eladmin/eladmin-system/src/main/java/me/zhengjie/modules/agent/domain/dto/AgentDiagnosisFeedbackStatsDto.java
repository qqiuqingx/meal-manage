package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能排查客服反馈统计结果。
 */
@Data
public class AgentDiagnosisFeedbackStatsDto {

    private Long totalCount;

    private Long acceptedCount;

    private Long rejectedCount;

    private Long partialCount;

    private Double acceptedRate;

    private Map<String, Long> actualReasonDistribution = new LinkedHashMap<>();
}
