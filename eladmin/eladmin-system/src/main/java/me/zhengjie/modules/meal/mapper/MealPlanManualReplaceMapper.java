/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.meal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.meal.domain.MealPlanManualReplace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 排餐单手工换菜关系 Mapper
 * @author qqx
 * @date 2026-05-28
 **/
@Mapper
public interface MealPlanManualReplaceMapper extends BaseMapper<MealPlanManualReplace> {

    /**
     * 根据排餐计划ID查询未删除的手工换菜关系
     */
    List<MealPlanManualReplace> selectByMealPlanId(@Param("mealPlanId") Long mealPlanId);

    /**
     * 根据排餐计划ID软删除所有手工换菜关系
     */
    int softDeleteByMealPlanId(@Param("mealPlanId") Long mealPlanId);

    /**
     * 根据排餐计划ID物理删除历史软删记录，避免再次软删时命中唯一索引。
     *
     * @param mealPlanId 排餐计划ID
     * @return 删除条数
     */
    int deleteHistoryByMealPlanId(@Param("mealPlanId") Long mealPlanId);

    /**
     * 根据客户排餐记录ID列表查询关联的手工换菜关系
     */
    List<MealPlanManualReplace> selectByCustomerPlanIds(@Param("customerPlanIds") List<Long> customerPlanIds);
}
