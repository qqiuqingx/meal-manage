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

/**
 * 销售看板查询条件
 * @author qqx
 **/
@Data
public class SalesDashboardQueryCriteria {

    @ApiModelProperty(value = "开始日期，格式 yyyy-MM-dd")
    private String startDate;

    @ApiModelProperty(value = "结束日期，格式 yyyy-MM-dd")
    private String endDate;

    @ApiModelProperty(value = "销售渠道")
    private String customerSource;

    @ApiModelProperty(value = "销售员（父套餐ID）")
    private Long parentPackageId;

    @ApiModelProperty(value = "销售产品（子套餐ID）")
    private Long childPackageId;

    @ApiModelProperty(value = "分页偏移量，由 Service 层计算：page * size")
    private Integer offset;

    @ApiModelProperty(value = "每页条数")
    private Integer size;
}
