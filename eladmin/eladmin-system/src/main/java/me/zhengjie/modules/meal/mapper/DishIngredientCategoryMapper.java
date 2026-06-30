package me.zhengjie.modules.meal.mapper;

import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import me.zhengjie.modules.meal.domain.dto.CategoryIngredientMappingRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;

/**
 * 配料分类 Mapper
 * @author qqx
 * @date 2026-04-24
 **/
@Mapper
public interface DishIngredientCategoryMapper extends BaseMapper<DishIngredientCategory> {

    @Select("SELECT * FROM dish_ingredient_category " +
            "WHERE name = #{name} AND level = #{level} AND parent_id <=> #{parentId} " +
            "AND enabled = true LIMIT 1")
    DishIngredientCategory selectByNameAndLevel(
        @Param("name") String name,
        @Param("level") int level,
        @Param("parentId") Integer parentId
    );

    @Select("SELECT MAX(sort) FROM dish_ingredient_category " +
            "WHERE level = #{level} AND parent_id <=> #{parentId}")
    Integer selectMaxSort(@Param("level") int level, @Param("parentId") Integer parentId);

    @Select("SELECT * FROM dish_ingredient_category " +
            "WHERE parent_id = #{parentId} AND level = 2 AND enabled = true " +
            "ORDER BY sort ASC")
    List<DishIngredientCategory> selectByParentId(@Param("parentId") Integer parentId);

    /**
     * 查询一级分类名→配料名的映射
     */
    @Select("SELECT c1.name as categoryName, di.name as ingredientName " +
            "FROM dish_ingredient di " +
            "JOIN dish_ingredient_category c2 ON di.category_id = c2.id " +
            "JOIN dish_ingredient_category c1 ON c2.parent_id = c1.id " +
            "WHERE c1.enabled = true AND c2.enabled = true AND di.enabled = true")
    List<CategoryIngredientMappingRow> selectLevel1IngredientMapping();

    /**
     * 查询二级分类名→配料名的映射
     */
    @Select("SELECT c2.name as categoryName, di.name as ingredientName " +
            "FROM dish_ingredient di " +
            "JOIN dish_ingredient_category c2 ON di.category_id = c2.id " +
            "WHERE c2.enabled = true AND di.enabled = true")
    List<CategoryIngredientMappingRow> selectLevel2IngredientMapping();
}
