package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 智能排查排餐查询请求。
 */
@Data
public class AgentMealPlanLookupRequest {
    @NotBlank
    private String recordDate;

    @NotBlank
    private String mealType;
}
