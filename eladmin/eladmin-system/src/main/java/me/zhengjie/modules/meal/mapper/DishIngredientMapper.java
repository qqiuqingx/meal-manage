package me.zhengjie.modules.meal.mapper;

import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.domain.dto.DishIngredientDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 配料 Mapper
 * @author qqx
 */
@Mapper
public interface DishIngredientMapper extends BaseMapper<DishIngredient> {

    List<DishIngredient> findAll(DishIngredientQueryCriteria criteria, @Param("page") com.baomidou.mybatisplus.extension.plugins.pagination.Page<Object> page);

    List<DishIngredient> findById(@Param("id") Integer id);

    List<DishIngredient> findByDishId(@Param("dishId") Integer dishId);

    List<DishIngredientRelation> findRelationsByDishId(@Param("dishId") Integer dishId);

    void insertRelation(@Param("dishId") Integer dishId, @Param("dto") DishIngredientDto dto);

    void deleteRelationsByDishId(@Param("dishId") Integer dishId);
}
