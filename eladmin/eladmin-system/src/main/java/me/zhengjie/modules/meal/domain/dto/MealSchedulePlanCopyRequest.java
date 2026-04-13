package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 复制排餐周请求
 * @author qqx
 * @date 2026-04-13
 **/
@Data
public class MealSchedulePlanCopyRequest {

    @NotNull(message = "来源周不能为空")
    @Min(value = 1, message = "来源周必须在1到4之间")
    @Max(value = 4, message = "来源周必须在1到4之间")
    @ApiModelProperty(value = "来源周", required = true)
    private Integer fromWeek;

    @NotNull(message = "目标周不能为空")
    @Min(value = 1, message = "目标周必须在1到4之间")
    @Max(value = 4, message = "目标周必须在1到4之间")
    @ApiModelProperty(value = "目标周", required = true)
    private Integer toWeek;
}
