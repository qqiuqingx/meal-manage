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

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;

/**
* @author qqx
* @date 2026-03-14
**/
@Data
public class CustomerDietaryRestrictionsQueryCriteria{

    @ApiModelProperty(value = "页码", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页数据量", example = "10")
    private Integer size = 10;

    @ApiModelProperty(value = "客户名称")
    private String customerName;

    @ApiModelProperty(value = "特殊要求")
    private String specialNeeds;

    @ApiModelProperty(value = "忌口")
    private String restrictions;

    @ApiModelProperty(value = "餐数")
    private Integer num;

    @ApiModelProperty(value = "客户地址")
    private String customerAddress;

    @ApiModelProperty(value = "客户手机号")
    private String phone;

    @ApiModelProperty(value = "剩余餐数")
    private Integer remainingMeals;

    @ApiModelProperty(value = "客户套餐")
    private String mealPackage;

    @ApiModelProperty(value = "开始时间")
    private String startDate;

    @ApiModelProperty(value = "结束时间")
    private String endDate;

    @ApiModelProperty(value = "来源")
    private String source;
}