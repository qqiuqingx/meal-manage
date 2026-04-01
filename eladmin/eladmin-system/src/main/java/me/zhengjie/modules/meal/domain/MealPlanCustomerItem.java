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
 * 排餐计划菜品明细
 * @author qqx
 * @date 2026-03-31
 **/
@Getter
@Setter
@TableName("meal_plan_customer_item")
public class MealPlanCustomerItem extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "客户排餐结果ID")
    private Long customerPlanId;

    @ApiModelProperty(value = "菜品类型")
    private String dishType;

    @ApiModelProperty(value = "菜品ID")
    private Integer dishId;

    @ApiModelProperty(value = "菜品名称")
    private String dishName;

    @ApiModelProperty(value = "序号")
    private Integer seq;

    @ApiModelProperty(value = "是否删除")
    private Boolean deleted;

    @ApiModelProperty(value = "是否发生了替换")
    private Boolean isReplaced;

    @ApiModelProperty(value = "原本想选的菜品ID")
    private Integer originalDishId;

    @ApiModelProperty(value = "原本想选的菜品名称")
    private String originalDishName;

    @ApiModelProperty(value = "替换原因：ALLERGY-过敏，NOT_FOUND-当日不足，NEXT_DAY-次日回退")
    private String replaceReason;
}
