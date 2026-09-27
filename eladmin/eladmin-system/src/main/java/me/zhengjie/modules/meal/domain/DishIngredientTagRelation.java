package me.zhengjie.modules.meal.domain;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 配料与标签的多对多关联实体。
 */
@Getter
@Setter
public class DishIngredientTagRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联标签的配料ID。 */
    @ApiModelProperty(value = "配料ID")
    private Integer ingredientId;

    /** 被关联的标签ID。 */
    @ApiModelProperty(value = "标签ID")
    private Integer tagId;
}
