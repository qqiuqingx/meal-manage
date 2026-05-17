package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 智能排查诊断上下文请求
 */
@Data
public class MealPlanDiagnosisContextRequest {

    private Long customerId;

    private String customerCode;

    @NotBlank
    private String recordDate;

    @NotBlank
    private String mealType;
}
