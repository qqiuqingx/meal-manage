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
package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;

/**
 * 排餐计划服务
 * @author qqx
 * @date 2026-03-31
 **/
public interface MealPlanService {

    /**
     * 根据排餐日期和餐次生成排餐计划。
     *
     * @param recordDate 排餐日期，格式 yyyy-MM-dd
     * @param mealType 餐次，支持 LUNCH / DINNER
     * @return 生成结果汇总
     */
    MealPlanGenerateResult generateMealPlan(String recordDate, String mealType);
}
