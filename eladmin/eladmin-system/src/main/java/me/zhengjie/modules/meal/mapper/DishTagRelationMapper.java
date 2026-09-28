package me.zhengjie.modules.meal.mapper;

import me.zhengjie.modules.meal.domain.DishTagRelation;
import me.zhengjie.modules.meal.domain.dto.DishTagRelationDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜品标签关联 Mapper。
 */
@Mapper
public interface DishTagRelationMapper {

    /**
     * 新增一条菜品与标签关联。
     * @param relation 菜品ID和标签ID
     * @return 新增记录数
     */
    int insertRelation(@Param("relation") DishTagRelation relation);

    /**
     * 删除给定菜品ID集合对应的标签关系。
     * @param dishIds 菜品ID集合
     * @return 删除记录数
     */
    int deleteByDishIds(@Param("dishIds") List<Integer> dishIds);

    /**
     * 统计指定标签被多少菜品引用。
     * @param tagId 标签ID
     * @return 关联记录数
     */
    long countRelationsByTagId(@Param("tagId") Integer tagId);

    /**
     * 批量读取菜品关联的标签。
     * @param dishIds 菜品ID集合
     * @return 菜品ID、标签ID和标签名称
     */
    List<DishTagRelationDto> selectTagsByDishIds(@Param("dishIds") List<Integer> dishIds);
}
