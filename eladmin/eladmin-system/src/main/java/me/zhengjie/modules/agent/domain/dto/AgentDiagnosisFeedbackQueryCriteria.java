package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查客服反馈查询条件。
 */
@Data
public class AgentDiagnosisFeedbackQueryCriteria {

    private String requestId;

    private Long customerId;

    private String recordDate;

    private String mealType;

    private String accepted;

    private String actualReasonCode;

    private Integer page = 0;

    private Integer size = 10;
}
