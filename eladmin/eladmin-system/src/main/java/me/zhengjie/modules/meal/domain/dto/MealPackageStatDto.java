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
import lombok.Data;

/**
 * 父套餐餐数统计DTO
 * @author qqx
 * @date 2026-04-05
 **/
@Data
public class MealPackageStatDto {

    @ApiModelProperty(value = "套餐代码")
    private String packageCode;

    @ApiModelProperty(value = "套餐名称")
    private String packageName;

    @ApiModelProperty(value = "餐数")
    private Integer mealCount;
}
