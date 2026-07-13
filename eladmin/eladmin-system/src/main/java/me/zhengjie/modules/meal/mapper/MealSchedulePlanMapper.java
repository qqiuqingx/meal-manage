package me.zhengjie.modules.meal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.MealSchedulePlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 排餐坑位 Mapper
 * @author qqx
 * @date 2026-04-13
 **/
@Mapper
public interface MealSchedulePlanMapper extends BaseMapper<MealSchedulePlan> {

    List<Dish> findBySchedule(@Param("weekNum") Integer weekNum, @Param("dayOfWeek") Integer dayOfWeek, @Param("mealTime") String mealTime);

    /**
     * 查询指定菜单周次、星期和受控餐次集合的排期菜品。
     *
     * @param weekNum 菜单周次
     * @param dayOfWeek 菜单星期
     * @param mealTimes 仅由服务端白名单生成的餐次集合
     * @return 按餐次、菜品排序的排期菜品
     */
    List<Dish> findByScheduleMealTimes(@Param("weekNum") Integer weekNum, @Param("dayOfWeek") Integer dayOfWeek,
                                       @Param("mealTimes") List<String> mealTimes);

    List<MealSchedulePlan> findByWeek(@Param("weekNum") Integer weekNum);

    Integer countBySlot(@Param("weekNum") Integer weekNum,
                        @Param("dayOfWeek") Integer dayOfWeek,
                        @Param("mealTime") String mealTime,
                        @Param("dishCategory") String dishCategory,
                        @Param("excludeId") Long excludeId);

    List<MealSchedulePlan> findByWeekForCopy(@Param("weekNum") Integer weekNum);
}
