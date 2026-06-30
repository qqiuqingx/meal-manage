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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 退餐日志查询条件
 * @author qqx
 * @date 2026-04-19
 **/
@Getter
@Setter
@ApiModel(value = "退餐日志查询条件")
public class MealRefundQueryCriteria {

    @ApiModelProperty(value = "客户姓名")
    private String customerName;

    @ApiModelProperty(value = "订单ID")
    private Long orderId;

    @ApiModelProperty(value = "操作时间开始，格式 yyyy-MM-dd")
    private String operateTimeStart;

    @ApiModelProperty(value = "操作时间结束，格式 yyyy-MM-dd")
    private String operateTimeEnd;

    @ApiModelProperty(value = "操作人")
    private String operator;

    @ApiModelProperty(value = "页码")
    private Integer page = 0;

    @ApiModelProperty(value = "每页数量")
    private Integer size = 10;
}
