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

import java.util.List;

/**
 * 销售看板 TOP3 汇总
 * @author qqx
 **/
@Data
public class SalesDashboardTopVO {

    @ApiModelProperty(value = "产品销售数量 TOP3")
    private List<SalesTopItemVO> productQuantityTop3;

    @ApiModelProperty(value = "产品销售金额 TOP3")
    private List<SalesTopItemVO> productAmountTop3;

    @ApiModelProperty(value = "销售员业绩 TOP3")
    private List<SalesTopItemVO> salespersonTop3;

    @ApiModelProperty(value = "销售渠道 TOP3")
    private List<SalesTopItemVO> channelTop3;
}
