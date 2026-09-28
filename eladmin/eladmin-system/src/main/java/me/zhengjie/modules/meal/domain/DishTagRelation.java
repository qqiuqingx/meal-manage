package me.zhengjie.modules.meal.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 菜品与标签的关联实体。
 */
@Getter
@Setter
public class DishTagRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联菜品ID。 */
    private Integer dishId;

    /** 关联标签ID。 */
    private Integer tagId;
}
