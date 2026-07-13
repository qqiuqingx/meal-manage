package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/** Agent 按客户、日期和餐次预览候选菜的受控请求。 */
@Data
public class AgentDishCandidateRequest {
    /** 客户稳定 ID。 */
    @NotNull
    private Long customerId;
    /** 排餐日期，格式 yyyy-MM-dd。 */
    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String recordDate;
    /** 餐次代码，仅支持午餐或晚餐。 */
    @NotBlank
    @Pattern(regexp = "LUNCH|DINNER")
    private String mealType;
}
