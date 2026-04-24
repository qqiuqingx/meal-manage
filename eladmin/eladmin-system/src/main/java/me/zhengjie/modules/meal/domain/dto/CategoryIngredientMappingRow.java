package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;

/**
 * 分类与配料名称映射行
 */
@Data
public class CategoryIngredientMappingRow {

    private String categoryName;

    private String ingredientName;
}
