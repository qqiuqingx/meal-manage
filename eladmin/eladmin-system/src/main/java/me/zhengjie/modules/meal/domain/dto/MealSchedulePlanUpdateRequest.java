package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 排餐坑位更新请求
 * @author qqx
 * @date 2026-04-13
 **/
@Data
public class MealSchedulePlanUpdateRequest {

    @NotNull(message = "dishId不能为空")
    @ApiModelProperty(value = "菜品ID", required = true)
    private Integer dishId;

    @ApiModelProperty(value = "是否启用")
    private Boolean enabled;
}
