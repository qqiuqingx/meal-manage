package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 后台发起智能排查请求。
 */
@Data
public class AgentDiagnosisRequest {

    private Long customerId;

    private String customerCode;

    @NotBlank
    private String recordDate;

    @NotBlank
    private String mealType;
}
