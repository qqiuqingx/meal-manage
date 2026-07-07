package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查规则缺口查询条件。
 */
@Data
public class AgentRuleGapQueryCriteria {

    private String status;

    private String gapType;

    private String actualReasonCode;

    private String recordDate;

    private Integer page = 0;

    private Integer size = 10;
}
