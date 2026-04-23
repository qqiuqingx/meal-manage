package me.zhengjie.modules.meal.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.domain.dto.DishIngredientDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 配料 Mapper
 * @author qqx
 */
@Mapper
public interface DishIngredientMapper extends BaseMapper<DishIngredient> {

    IPage<DishIngredient> selectPageByCriteria(@Param("criteria") DishIngredientQueryCriteria criteria, Page<DishIngredient> page);

    List<DishIngredient> selectPageByCriteria(@Param("criteria") DishIngredientQueryCriteria criteria);

    List<DishIngredient> findById(@Param("id") Integer id);

    List<DishIngredient> findByDishId(@Param("dishId") Integer dishId);

    List<DishIngredientRelation> findRelationsByDishId(@Param("dishId") Integer dishId);

    List<DishIngredientRelation> findRelationsByDishIds(@Param("dishIds") List<Integer> dishIds);

    void insertRelation(@Param("dishId") Integer dishId, @Param("dto") DishIngredientDto dto);

    void deleteRelationsByDishId(@Param("dishId") Integer dishId);
}
