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
import me.zhengjie.modules.meal.domain.MealRefundLog;

/**
 * 退餐日志VO（包含关联信息）
 * @author qqx
 * @date 2026-04-19
 **/
@Getter
@Setter
@ApiModel(value = "退餐日志VO")
public class MealRefundLogVO extends MealRefundLog {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "客户姓名")
    private String customerName;

    @ApiModelProperty(value = "客户编号")
    private String customerCode;

    @ApiModelProperty(value = "订单编号")
    private String orderCode;
}
