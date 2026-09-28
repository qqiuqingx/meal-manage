package me.zhengjie.modules.meal.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 菜品标签分页查询条件。
 */
@Getter
@Setter
public class DishTagQueryCriteria {

    /** 标签名称模糊查询条件。 */
    private String name;

    /** 零起始页码。 */
    private Integer page = 0;

    /** 每页记录数。 */
    private Integer size = 20;
}
