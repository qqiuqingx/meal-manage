package me.zhengjie.modules.meal.mapper;

import me.zhengjie.modules.meal.domain.DishIngredientTagRelation;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagRelationDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 配料标签关联 Mapper。
 */
@Mapper
public interface DishIngredientTagRelationMapper {

    /**
     * 新增一条配料与标签关联。
     * @param relation 配料ID和标签ID
     * @return 新增记录数
     */
    int insertRelation(@Param("relation") DishIngredientTagRelation relation);

    /**
     * 删除指定配料的全部标签关系。
     * @param ingredientId 配料ID
     * @return 删除记录数
     */
    int deleteByIngredientId(@Param("ingredientId") Integer ingredientId);

    /**
     * 按给定配料ID集合清理标签关系。
     * @param ingredientIds 配料ID集合
     * @return 删除记录数
     */
    int deleteByIngredientIds(@Param("ingredientIds") List<Integer> ingredientIds);

    /**
     * 批量读取配料关联的标签。
     * @param ingredientIds 配料ID集合
     * @return 配料ID、标签ID和标签名称
     */
    List<DishIngredientTagRelationDto> selectTagsByIngredientIds(@Param("ingredientIds") List<Integer> ingredientIds);
}
