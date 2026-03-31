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
import me.zhengjie.modules.meal.domain.MealPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 排餐计划主表 Mapper
 * @author qqx
 * @date 2026-03-31
 **/
@Mapper
public interface MealPlanMapper extends BaseMapper<MealPlan> {

    MealPlan findActiveByDateAndMealType(@Param("recordDate") LocalDate recordDate, @Param("mealType") String mealType);

    MealPlan findActiveByDateAndMealTypeForUpdate(@Param("recordDate") LocalDate recordDate, @Param("mealType") String mealType);

    int softDeleteItemsByMealPlanId(@Param("mealPlanId") Long mealPlanId);

    int softDeleteCustomersByMealPlanId(@Param("mealPlanId") Long mealPlanId);

    int softDeletePlanById(@Param("id") Long id);
}
