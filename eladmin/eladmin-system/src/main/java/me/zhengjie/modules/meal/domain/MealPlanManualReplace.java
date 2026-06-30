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
package me.zhengjie.modules.meal.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import me.zhengjie.base.BaseEntity;

/**
 * 排餐单手工换菜关系表
 * @author qqx
 * @date 2026-05-28
 **/
@Getter
@Setter
@TableName("meal_plan_manual_replace")
public class MealPlanManualReplace extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "排餐计划ID")
    private Long mealPlanId;

    @ApiModelProperty(value = "客户排餐记录ID")
    private Long customerPlanId;

    @ApiModelProperty(value = "客户ID")
    private Long customerId;

    @ApiModelProperty(value = "客户编号快照")
    private String customerCode;

    @ApiModelProperty(value = "客户姓名快照")
    private String customerName;

    @ApiModelProperty(value = "换菜菜品ID")
    private Integer dishId;

    @ApiModelProperty(value = "换菜菜名快照")
    private String dishName;

    @ApiModelProperty(value = "菜品类目：MAIN/SIDE/VEGETABLE/SOUP/RICE")
    private String dishType;

    @ApiModelProperty(value = "是否删除")
    private Boolean deleted;
}
