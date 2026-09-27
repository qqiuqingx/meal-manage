package me.zhengjie.modules.meal.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 标签关联查询结果，用于按配料ID批量组装标签列表。
 */
@Getter
@Setter
public class DishIngredientTagRelationDto {

    /** 关联标签的配料ID。 */
    private Integer ingredientId;

    /** 查询出的标签ID。 */
    private Integer tagId;

    /** 查询出的标签名称。 */
    private String name;
}
