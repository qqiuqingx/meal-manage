package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;

/**
 * 配料查询条件
 * @author qqx
 * @date 2026-03-15
 **/
@Data
public class DishIngredientQueryCriteria {

    private String name;

    private String category;

    private Boolean enabled;

    private Integer page = 0;

    private Integer size = 10;
}
