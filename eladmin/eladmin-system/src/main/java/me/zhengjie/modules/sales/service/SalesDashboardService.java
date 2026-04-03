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
package me.zhengjie.modules.sales.service;

import me.zhengjie.modules.sales.domain.dto.*;
import me.zhengjie.utils.PageResult;

/**
 * 销售看板 Service
 * @author qqx
 **/
public interface SalesDashboardService {

    /**
     * 销售金额概览（四卡片）
     */
    SalesOverviewVO getOverview(String startDate, String endDate);

    /**
     * 月度销售金额趋势
     */
    SalesMonthlyTrendVO getMonthlyTrend(Integer year);

    /**
     * TOP3 汇总
     */
    SalesDashboardTopVO getTop(String startDate, String endDate);

    /**
     * 销售明细表（分页）
     */
    PageResult<SalesDetailVO> getDetail(SalesDashboardQueryCriteria criteria, Integer page, Integer size);

    /**
     * 销售渠道汇总卡片
     */
    SalesChannelSummaryVO getChannelSummary(String customerSource, String startDate, String endDate);

    /**
     * 销售员汇总卡片
     */
    SalesSalespersonSummaryVO getSalespersonSummary(Long parentPackageId, String startDate, String endDate);
}
