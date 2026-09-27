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

    /** 旧分类字段（兼容） */
    private String category;

    /** 一级分类ID */
    private Integer parentCategoryId;

    /** 二级分类ID */
    private Integer categoryId;

    /** 标签ID；查询时与名称、分类和状态条件同时生效。 */
    private Integer tagId;

    private Boolean enabled;

    private Integer page = 0;

    private Integer size = 10;
}
