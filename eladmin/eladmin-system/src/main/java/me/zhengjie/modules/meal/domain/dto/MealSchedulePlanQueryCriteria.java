package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 排餐坑位查询条件
 * @author qqx
 * @date 2026-04-13
 **/
@Data
public class MealSchedulePlanQueryCriteria {

    @NotNull(message = "weekNum不能为空")
    @Min(value = 1, message = "weekNum必须在1到4之间")
    @Max(value = 4, message = "weekNum必须在1到4之间")
    @ApiModelProperty(value = "周序号(1-4)", required = true)
    private Integer weekNum;
}
