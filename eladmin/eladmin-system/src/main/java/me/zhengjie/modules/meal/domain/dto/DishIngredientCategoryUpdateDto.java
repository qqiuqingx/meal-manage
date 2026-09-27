package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 编辑配料分类请求；分类层级和父级由服务端保持不变。
 */
@Getter
@Setter
public class DishIngredientCategoryUpdateDto {

    /** 新分类名称。 */
    @ApiModelProperty(value = "分类名称，去除首尾空白后不能为空且最多64个字符")
    private String name;

    /** 可选的新排序值；为空时保留原排序。 */
    @ApiModelProperty(value = "排序，为空时保留原值")
    private Integer sort;
}
