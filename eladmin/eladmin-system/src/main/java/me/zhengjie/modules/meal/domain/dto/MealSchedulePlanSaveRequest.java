package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 排餐坑位新增请求
 * @author qqx
 * @date 2026-04-13
 **/
@Data
public class MealSchedulePlanSaveRequest {

    @NotNull(message = "weekNum不能为空")
    @Min(value = 1, message = "weekNum必须在1到4之间")
    @Max(value = 4, message = "weekNum必须在1到4之间")
    @ApiModelProperty(value = "周序号(1-4)", required = true)
    private Integer weekNum;

    @NotNull(message = "dayOfWeek不能为空")
    @Min(value = 1, message = "dayOfWeek必须在1到7之间")
    @Max(value = 7, message = "dayOfWeek必须在1到7之间")
    @ApiModelProperty(value = "星期几(1-7)", required = true)
    private Integer dayOfWeek;

    @NotBlank(message = "mealTime不能为空")
    @ApiModelProperty(value = "餐次", required = true)
    private String mealTime;

    @NotBlank(message = "dishCategory不能为空")
    @ApiModelProperty(value = "坑位分类", required = true)
    private String dishCategory;

    @NotNull(message = "dishId不能为空")
    @ApiModelProperty(value = "菜品ID", required = true)
    private Integer dishId;
}
