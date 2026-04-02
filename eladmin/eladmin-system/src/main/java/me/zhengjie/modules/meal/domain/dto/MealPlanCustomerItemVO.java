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
 * 排餐计划菜品明细查询结果
 * @author qqx
 * @date 2026-04-02
 **/
@Data
public class MealPlanCustomerItemVO {

    @ApiModelProperty(value = "明细ID")
    private Long id;

    @ApiModelProperty(value = "菜品类型（MAIN主菜/SIDE副菜/VEGETABLE素菜/SOUP汤/RICE米饭）")
    private String dishType;

    @ApiModelProperty(value = "菜品ID")
    private Integer dishId;

    @ApiModelProperty(value = "菜品名称")
    private String dishName;

    @ApiModelProperty(value = "序号")
    private Integer seq;

    @ApiModelProperty(value = "是否发生了替换")
    private Boolean isReplaced;

    @ApiModelProperty(value = "原本想选的菜品ID")
    private Integer originalDishId;

    @ApiModelProperty(value = "原本想选的菜品名称")
    private String originalDishName;

    @ApiModelProperty(value = "替换原因（ALLERGY过敏/NEXT_DAY次日回退）")
    private String replaceReason;

    @ApiModelProperty(value = "配料列表")
    private java.util.List<DishIngredientItemVO> ingredients;
}
