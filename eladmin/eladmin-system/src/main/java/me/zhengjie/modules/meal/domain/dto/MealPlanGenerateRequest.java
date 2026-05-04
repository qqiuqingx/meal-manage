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
package me.zhengjie.modules.meal.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * 排餐计划生成请求
 * @author qqx
 * @date 2026-03-31
 **/
@Getter
@Setter
public class MealPlanGenerateRequest {

    @NotBlank(message = "排餐日期不能为空")
    @ApiModelProperty(value = "排餐日期，格式yyyy-MM-dd")
    private String recordDate;

    @NotBlank(message = "餐次不能为空")
    @ApiModelProperty(value = "餐次，仅支持BREAKFAST/LUNCH/DINNER")
    private String mealType;

    @ApiModelProperty(value = "指定客户ID，不传则生成全部客户的排餐计划")
    private Long customerId;

    @Min(value = 1, message = "menuWeekNum范围仅支持1-4")
    @Max(value = 4, message = "menuWeekNum范围仅支持1-4")
    @ApiModelProperty(value = "指定菜单周次，需与menuDayOfWeek同时传入；不传则按recordDate推导，范围1-4")
    private Integer menuWeekNum;

    @Min(value = 1, message = "menuDayOfWeek范围仅支持1-7")
    @Max(value = 7, message = "menuDayOfWeek范围仅支持1-7")
    @ApiModelProperty(value = "指定菜单星期，需与menuWeekNum同时传入；不传则按recordDate推导，范围1-7")
    private Integer menuDayOfWeek;
}
