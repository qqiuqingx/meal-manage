package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import java.util.List;

/**
 * 菜品配料关联 DTO
 * @author qqx
 * @date 2026-03-15
 **/
@Data
@ApiModel("菜品配料关联")
public class DishIngredientDto implements Serializable {

    @ApiModelProperty(value = "配料ID")
    private Integer ingredientId;

    @ApiModelProperty(value = "配料名称（用于展示）")
    private String ingredientName;

    @ApiModelProperty(value = "用量")
    private Double quantity;

    @ApiModelProperty(value = "单位")
    private String unit;

    @ApiModelProperty(value = "备注")
    private String remark;
}
