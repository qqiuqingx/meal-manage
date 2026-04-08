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

import java.util.Date;

/**
 * 核销记录列表展示 VO
 * @author qqx
 * @date 2026-04-08
 **/
@Data
public class MealVerificationLogVO {

    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "客户排餐ID")
    private Long mealPlanCustomerId;

    @ApiModelProperty(value = "排餐计划ID")
    private Long mealPlanId;

    @ApiModelProperty(value = "客户ID")
    private Long customerId;

    @ApiModelProperty(value = "关联订单ID")
    private Long orderId;

    @ApiModelProperty(value = "排餐日期")
    private Date recordDate;

    @ApiModelProperty(value = "餐次（LUNCH/DINNER）")
    private String mealType;

    @ApiModelProperty(value = "核销餐数")
    private Integer verificationCount;

    @ApiModelProperty(value = "核销前剩余餐数")
    private Integer remainingBefore;

    @ApiModelProperty(value = "核销后剩余餐数")
    private Integer remainingAfter;

    @ApiModelProperty(value = "核销前已核销总数")
    private Integer verifiedTotalBefore;

    @ApiModelProperty(value = "核销后已核销总数")
    private Integer verifiedTotalAfter;

    @ApiModelProperty(value = "操作人")
    private String operator;

    @ApiModelProperty(value = "操作时间")
    private Date operateTime;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    // ---- 关联字段 ----

    @ApiModelProperty(value = "客户名称")
    private String customerName;

    @ApiModelProperty(value = "客户编码")
    private String customerCode;

    @ApiModelProperty(value = "父套餐名称")
    private String parentPackageName;
}
