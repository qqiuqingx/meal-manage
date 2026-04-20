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
 * 销售看板金额概览
 * @author qqx
 **/
@Data
public class SalesOverviewVO {

    @ApiModelProperty(value = "今日销售金额")
    private BigDecimal todayAmount;

    @ApiModelProperty(value = "本周销售金额")
    private BigDecimal weekAmount;

    @ApiModelProperty(value = "本月销售金额")
    private BigDecimal monthAmount;

    @ApiModelProperty(value = "累计销售金额")
    private BigDecimal totalAmount;

    // ── 核销金额 ──────────────────────────────────────────

    @ApiModelProperty(value = "今日核销金额")
    private BigDecimal todayVerificationAmount;

    @ApiModelProperty(value = "本周核销金额")
    private BigDecimal weekVerificationAmount;

    @ApiModelProperty(value = "本月核销金额")
    private BigDecimal monthVerificationAmount;

    @ApiModelProperty(value = "累计核销金额")
    private BigDecimal totalVerificationAmount;
}
