package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;

/**
 * 菜品查询条件
 * @author qqx
 * @date 2026-03-14
 **/
@Data
public class DishQueryCriteria {

    @ApiModelProperty(value = "菜品名称")
    private String name;

    @ApiModelProperty(value = "菜品类型")
    private String dishType;

    @ApiModelProperty(value = "餐次")
    private String mealType;

    @ApiModelProperty(value = "套餐")
    private String mealPackage;

    @ApiModelProperty(value = "是否启用")
    private Boolean enabled;

    @ApiModelProperty(value = "排期日期，格式：yyyy-MM-dd")
    private String scheduleDate;

    @ApiModelProperty(value = "排期匹配的菜品ID列表（内部字段）", hidden = true)
    private List<Integer> scheduledDishIds;

    @ApiModelProperty(value = "页码")
    private Integer page = 0;

    @ApiModelProperty(value = "每页数量")
    private Integer size = 10;
}
