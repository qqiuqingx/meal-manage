package me.zhengjie.modules.meal.mapper;

import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 菜品 Mapper 接口
 * @author qqx
 * @date 2026-03-14
 **/
@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    IPage<Dish> findAll(@Param("criteria") DishQueryCriteria criteria, Page<Object> page);

    List<Dish> findAll(@Param("criteria") DishQueryCriteria criteria);

    List<Dish> findBySchedule(@Param("week") Integer week, @Param("day") Integer day, @Param("mealType") String mealType);

    List<Dish> findAvailableByCustomerId(@Param("customerId") Integer customerId, @Param("mealType") String mealType, @Param("week") Integer week, @Param("day") Integer day);
}
