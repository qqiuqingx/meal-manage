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

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;

/**
 * 排餐计划查询条件
 * @author qqx
 * @date 2026-04-02
 **/
@Data
public class MealPlanQueryCriteria {

    @ApiModelProperty(value = "排餐日期，格式 yyyy-MM-dd")
    private String recordDate;

    @ApiModelProperty(value = "餐次（LUNCH午餐/DINNER晚餐）")
    private String mealType;

    @ApiModelProperty(value = "状态（GENERATING生成中/SUCCESS成功/FAILED失败）")
    private String status;

    @ApiModelProperty(value = "页码")
    private Integer page = 0;

    @ApiModelProperty(value = "每页数量")
    private Integer size = 10;
}
