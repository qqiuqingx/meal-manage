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

import me.zhengjie.modules.meal.domain.dto.MealPlanManualReplaceSaveRequest;
import me.zhengjie.modules.meal.domain.dto.MealPlanManualReplaceVO;

import java.util.List;

/**
 * 排餐单手工换菜服务
 * @author qqx
 * @date 2026-05-28
 **/
public interface MealPlanManualReplaceService {

    /**
     * 查询指定排餐计划的手工换菜关系列表
     *
     * @param mealPlanId 排餐计划ID
     * @return 手工换菜关系列表
     */
    List<MealPlanManualReplaceVO> queryByMealPlanId(Long mealPlanId);

    /**
     * 全量保存指定排餐计划的手工换菜关系。
     * 采用"先软删、再插入"的覆盖式写入，保证幂等。
     *
     * @param mealPlanId 排餐计划ID
     * @param request    换菜保存请求
     */
    void saveManualReplaces(Long mealPlanId, MealPlanManualReplaceSaveRequest request);
}
