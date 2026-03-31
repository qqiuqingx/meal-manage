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

import java.util.ArrayList;
import java.util.List;

/**
 * 排餐计划生成结果
 * @author qqx
 * @date 2026-03-31
 **/
@Getter
@Setter
public class MealPlanGenerateResult {

    @ApiModelProperty(value = "排餐计划ID")
    private Long mealPlanId;

    @ApiModelProperty(value = "排餐日期")
    private String recordDate;

    @ApiModelProperty(value = "餐次")
    private String mealType;

    @ApiModelProperty(value = "总数")
    private Integer totalCount;

    @ApiModelProperty(value = "成功数")
    private Integer successCount;

    @ApiModelProperty(value = "失败数")
    private Integer failCount;

    @ApiModelProperty(value = "失败明细")
    private List<FailDetail> failDetails = new ArrayList<>();

    @Getter
    @Setter
    public static class FailDetail {

        @ApiModelProperty(value = "失败原因")
        private String failReason;
    }
}
