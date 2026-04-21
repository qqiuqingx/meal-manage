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
package me.zhengjie.modules.sales.domain.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售明细行
 * @author qqx
 **/
@Data
public class SalesDetailVO {

    @ApiModelProperty(value = "订单ID")
    private Long id;

    @ApiModelProperty(value = "销售日期")
    private String saleDate;

    @ApiModelProperty(value = "销售产品")
    private String productName;

    @ApiModelProperty(value = "销售餐数")
    private Integer mealCount;

    @ApiModelProperty(value = "客户备注")
    private String customerRemark;

    @ApiModelProperty(value = "销售金额")
    private BigDecimal saleAmount;

    @ApiModelProperty(value = "销售渠道")
    private String channelName;

    @ApiModelProperty(value = "销售员")
    private String salespersonName;

    @ApiModelProperty(value = "主菜/荤菜数量")
    private Integer mainDishCount;

    @ApiModelProperty(value = "副菜数量")
    private Integer sideDishCount;

    @ApiModelProperty(value = "素菜数量")
    private Integer vegCount;

    @ApiModelProperty(value = "米饭数量")
    private Integer riceCount;

    @ApiModelProperty(value = "汤数量")
    private Integer soupCount;
}
