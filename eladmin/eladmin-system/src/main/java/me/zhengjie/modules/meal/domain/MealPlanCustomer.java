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
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import me.zhengjie.base.BaseEntity;

import java.time.LocalDate;

/**
 * 排餐计划客户结果
 * @author qqx
 * @date 2026-03-31
 **/
@Getter
@Setter
@TableName("meal_plan_customer")
public class MealPlanCustomer extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "排餐计划ID")
    private Long mealPlanId;

    @ApiModelProperty(value = "客户ID")
    private Long customerId;

    @ApiModelProperty(value = "客户姓名")
    private String customerName;

    @ApiModelProperty(value = "手机号")
    private String phone;

    @ApiModelProperty(value = "客户编码")
    @TableField(exist = false)
    private String customerCode;

    @ApiModelProperty(value = "订单ID")
    private Long orderId;

    @ApiModelProperty(value = "父套餐ID")
    private Long parentPackageId;

    @ApiModelProperty(value = "子套餐ID")
    private Long childPackageId;

    @ApiModelProperty(value = "状态")
    private Integer status;

    @ApiModelProperty(value = "是否已核销（0未核销，1已核销）")
    private Integer isVerified;

    @ApiModelProperty(value = "核销时间")
    private java.util.Date verificationTime;

    @ApiModelProperty(value = "核销操作人")
    private String verificationOperator;

    @ApiModelProperty(value = "失败原因")
    private String failReason;

    @ApiModelProperty(value = "荤菜需求数")
    private Integer meatRequiredCount;

    @ApiModelProperty(value = "素菜需求数")
    private Integer vegRequiredCount;

    @ApiModelProperty(value = "是否含汤")
    private Integer includeSoup;

    @ApiModelProperty(value = "是否含米饭")
    private Integer includeRice;

    @ApiModelProperty(value = "是否删除")
    private Boolean deleted;

    /**
     * 补主菜数量 = max(0, 主菜需求数 - 每日菜单固定提供1个)
     */
    @ApiModelProperty(value = "补主菜数量")
    private Integer supplementaryMainCount;

    /**
     * 补副菜数量 = max(0, 副菜需求数 - 每日菜单固定提供1个)
     */
    @ApiModelProperty(value = "补副菜数量")
    private Integer supplementarySideCount;

    /**
     * 补素菜数量 = max(0, 素菜需求数 - 每日菜单固定提供1个)
     */
    @ApiModelProperty(value = "补素菜数量")
    private Integer supplementaryVegCount;

    /**
     * 补米饭数量 = max(0, 米饭需求数 - 每日菜单固定提供1个)
     */
    @ApiModelProperty(value = "补米饭数量")
    private Integer supplementaryRiceCount;

        /**
     * 补汤数量 = max(0, 汤需求数 - 每日菜单固定提供1个)
     */
    @ApiModelProperty(value = "补汤数量")
    private Integer supplementarySoupCount;

    /**
     * 早餐配送数量（BREAKFAST 餐次专用）
     */
    @ApiModelProperty(value = "早餐配送数量")
    private Integer breakfastCount;

    /**
     * 特殊要求（关联查询填充）
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "特殊要求")
    private String specialRequirements;

    /**
     * 客户生产日期（关联 customer_profile 查询填充）
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "客户生产日期")
    private LocalDate productionDate;
}
