package me.zhengjie.modules.meal.domain.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 配料标签对外展示信息。
 */
@Getter
@Setter
public class DishIngredientTagDto {

    /** 标签ID。 */
    private Integer id;

    /** 标签显示名称。 */
    private String name;
}
