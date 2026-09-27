package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 新增配料分类请求。
 */
@Getter
@Setter
public class DishIngredientCategoryCreateDto {

    /** 分类名称。 */
    @ApiModelProperty(value = "分类名称，去除首尾空白后不能为空且最多64个字符")
    private String name;

    /** 分类层级，只允许一级或二级。 */
    @ApiModelProperty(value = "层级：1一级分类，2二级分类")
    private Integer level;

    /** 二级分类所属的一级分类ID；新增一级分类时必须为空。 */
    @ApiModelProperty(value = "父分类ID，一级分类为空")
    private Integer parentId;

    /** 可选排序值；为空时追加到同级分类末尾。 */
    @ApiModelProperty(value = "排序，为空时追加到同级末尾")
    private Integer sort;
}
