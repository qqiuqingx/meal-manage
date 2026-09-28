package me.zhengjie.modules.meal.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 菜品标签关联查询结果，用于批量组装菜品标签列表。
 */
@Getter
@Setter
public class DishTagRelationDto {

    /** 菜品ID。 */
    private Integer dishId;

    /** 查询出的标签ID。 */
    private Integer tagId;

    /** 查询出的标签名称。 */
    private String name;
}
