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

import java.io.Serializable;
import java.util.Date;

/**
 * 核销日志实体
 * @author qqx
 * @date 2026-04-05
 **/
@Getter
@Setter
@TableName("meal_verification_log")
public class MealVerificationLog implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
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

    @ApiModelProperty(value = "是否删除（0未删除 1已删除）")
    private Integer deleted;

    @ApiModelProperty(value = "删除时间")
    private Date deleteTime;

    @ApiModelProperty(value = "删除操作人")
    private String deletedBy;

    @ApiModelProperty(value = "是否已退餐（0=否，1=是）")
    private Integer isRefunded;

    @ApiModelProperty(value = "退餐时间")
    private Date refundTime;
}
