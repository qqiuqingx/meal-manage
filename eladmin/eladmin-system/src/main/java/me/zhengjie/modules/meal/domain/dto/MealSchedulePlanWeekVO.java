package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import me.zhengjie.modules.meal.domain.MealSchedulePlan;

import java.util.List;

/**
 * 周排餐网格
 * @author qqx
 * @date 2026-04-13
 **/
@Data
public class MealSchedulePlanWeekVO {

    @ApiModelProperty(value = "周序号")
    private Integer weekNum;

    @ApiModelProperty(value = "坑位列表")
    private List<MealSchedulePlan> slots;
}
