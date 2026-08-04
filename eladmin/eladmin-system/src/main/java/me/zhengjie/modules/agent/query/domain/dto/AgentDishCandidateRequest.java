package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Agent 按客户、日期和餐次预览候选菜的受控请求。 */
@Data
public class AgentDishCandidateRequest {
    /** 客户稳定 ID。 */
    @Min(1)
    private Long customerId;
    /** 客户业务编号，可与客户 ID 二选一。 */
    @Size(max = 64)
    private String customerCode;
    /** 订单稳定 ID，可选。 */
    @Min(1)
    private Long orderId;
    /** 订单业务编号，可选。 */
    @Size(max = 64)
    private String orderCode;
    /** 排餐日期，格式 yyyy-MM-dd。 */
    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String recordDate;
    /** 餐次代码，仅支持午餐或晚餐。 */
    @NotBlank
    @Pattern(regexp = "LUNCH|DINNER")
    private String mealType;
}
