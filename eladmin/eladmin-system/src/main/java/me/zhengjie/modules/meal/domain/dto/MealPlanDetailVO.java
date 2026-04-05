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

        @ApiModelProperty(value = "菜品明细列表")
        private List<MealPlanCustomerItemVO> items;
    }
}
