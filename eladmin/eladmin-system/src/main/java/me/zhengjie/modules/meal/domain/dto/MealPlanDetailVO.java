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

import java.util.List;

/**
 * 排餐计划完整详情（聚合查询）
 * @author qqx
 * @date 2026-04-02
 **/
@Data
public class MealPlanDetailVO {

    @ApiModelProperty(value = "排餐计划主表信息")
    private MealPlanVO mealPlan;

    @ApiModelProperty(value = "客户排餐列表")
    private List<CustomerPlanDetail> customers;

    @ApiModelProperty(value = "手工换菜关系列表")
    private List<MealPlanManualReplaceVO> manualReplaces;

    @ApiModelProperty(value = "客户总数")
    private Integer totalCustomers;

    @ApiModelProperty(value = "成功数")
    private Integer successCount;

    @ApiModelProperty(value = "失败数")
    private Integer failCount;

    @Data
    public static class MealPlanVO {
        @ApiModelProperty(value = "排餐计划ID")
        private Long id;

        @ApiModelProperty(value = "排餐日期")
        private String recordDate;

        @ApiModelProperty(value = "餐次")
        private String mealType;

        @ApiModelProperty(value = "总订单数")
        private Integer totalCount;

        @ApiModelProperty(value = "状态")
        private String status;

        @ApiModelProperty(value = "生成时间")
        private String generateTime;
    }

    @Data
    public static class CustomerPlanDetail {
        @ApiModelProperty(value = "客户计划ID")
        private Long id;

        @ApiModelProperty(value = "客户ID")
        private Long customerId;

        @ApiModelProperty(value = "客户姓名")
        private String customerName;

        @ApiModelProperty(value = "手机号")
        private String phone;

        @ApiModelProperty(value = "客户编码")
        private String customerCode;

        @ApiModelProperty(value = "是否订单当前餐次首次成功排餐")
        private Boolean firstMealOfOrder;

        @ApiModelProperty(value = "订单ID")
        private Long orderId;

        @ApiModelProperty(value = "父套餐ID")
        private Long parentPackageId;

        @ApiModelProperty(value = "子套餐ID")
        private Long childPackageId;

        @ApiModelProperty(value = "状态（0失败/1成功）")
        private Integer status;

        @ApiModelProperty(value = "失败原因")
        private String failReason;

        @ApiModelProperty(value = "荤菜需求数")
        private Integer meatRequiredCount;

        @ApiModelProperty(value = "素菜需求数")
        private Integer vegRequiredCount;

        @ApiModelProperty(value = "是否包含汤")
        private Integer includeSoup;

        @ApiModelProperty(value = "是否包含米饭")
        private Integer includeRice;

        @ApiModelProperty(value = "是否已核销（0未核销，1已核销）")
        private Integer isVerified;

        @ApiModelProperty(value = "核销时间")
        private String verificationTime;

        @ApiModelProperty(value = "核销操作人")
        private String verificationOperator;

        @ApiModelProperty(value = "菜品明细列表")
        private List<MealPlanCustomerItemVO> items;

        @ApiModelProperty(value = "补主菜数量")
        private Integer supplementaryMainCount;

        @ApiModelProperty(value = "补副菜数量")
        private Integer supplementarySideCount;

        @ApiModelProperty(value = "补素菜数量")
        private Integer supplementaryVegCount;

        @ApiModelProperty(value = "补米饭数量")
        private Integer supplementaryRiceCount;

        @ApiModelProperty(value = "补汤数量")
        private Integer supplementarySoupCount;

        @ApiModelProperty(value = "特殊要求")
        private String specialRequirements;

        @ApiModelProperty(value = "客户生产日期，格式 yyyy-MM-dd")
        private String productionDate;

        @ApiModelProperty(value = "是否处于生产当天至生产后3天内")
        private Boolean nearProductionDate;

        @ApiModelProperty(value = "排餐日期距离生产日期的天数，生产当天为0")
        private Integer productionDateDiffDays;
    }
}
