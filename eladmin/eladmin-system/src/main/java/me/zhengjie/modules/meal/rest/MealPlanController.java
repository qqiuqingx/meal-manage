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
package me.zhengjie.modules.meal.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.Limit;
import me.zhengjie.annotation.Log;
import me.zhengjie.aspect.LimitType;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateRequest;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.service.MealPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 排餐计划管理
 * @author qqx
 * @date 2026-03-31
 **/
@RestController
@RequiredArgsConstructor
@Api(tags = "排餐计划管理")
@RequestMapping("/api/meal-plan")
public class MealPlanController {

    private final MealPlanService mealPlanService;

    /**
     * 生成指定日期、指定餐次的排餐计划。
     *
     * @param request 排餐生成请求
     * @return 排餐生成结果
     */
    @Log("生成排餐计划")
    @ApiOperation("生成排餐计划")
    @PostMapping("/generate")
    @Limit(key = "generate", period = 60, count = 5, name = "generateMealPlan", prefix = "mealPlan", limitType = LimitType.IP)
    @PreAuthorize("@el.check('mealPlan:generate')")
    public ResponseEntity<MealPlanGenerateResult> generateMealPlan(@Validated @RequestBody MealPlanGenerateRequest request) {
        return new ResponseEntity<>(mealPlanService.generateMealPlan(request.getRecordDate(), request.getMealType()), HttpStatus.OK);
    }
}
