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

import java.io.Serializable;
import java.util.List;

/**
 * 排餐计划客户配送地址VO
 * @author qqx
 * @date 2026-04-13
 **/
@Data
public class MealPlanCustomerAddressVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "客户ID")
    private Long customerId;

    @ApiModelProperty(value = "客户编号")
    private String customerCode;

    @ApiModelProperty(value = "手机号")
    private String phone;

    @ApiModelProperty(value = "配送地址（根据排餐日期自动选择工作日/周末/默认地址）")
    private String addressDetail;

    @ApiModelProperty(value = "实际使用的地址类型（DEFAULT/WORKDAY/WEEKEND）")
    private String addressType;

    @ApiModelProperty(value = "过敏食物标签列表")
    private List<String> allergyTags;

    @ApiModelProperty(value = "特殊要求")
    private String specialRequirements;
}
