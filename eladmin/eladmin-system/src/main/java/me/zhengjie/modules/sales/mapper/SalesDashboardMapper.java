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
package me.zhengjie.modules.sales.mapper;

import me.zhengjie.modules.sales.domain.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 销售看板 Mapper
 * @author qqx
 **/
@Mapper
public interface SalesDashboardMapper {

    /**
     * 销售金额四卡片
     */
    SalesOverviewVO getOverview(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 月度销售金额趋势
     */
    List<SalesMonthlyItemVO> getMonthlyTrend(@Param("year") Integer year);

    /**
     * 产品销售数量 TOP3
     */
    List<SalesTopItemVO> getProductQuantityTop3(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 产品销售金额 TOP3
     */
    List<SalesTopItemVO> getProductAmountTop3(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 销售员业绩 TOP3
     */
    List<SalesTopItemVO> getSalespersonTop3(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 销售渠道 TOP3
     */
    List<SalesTopItemVO> getChannelTop3(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 销售明细表查询
     */
    List<SalesDetailVO> getDetailList(@Param("criteria") SalesDashboardQueryCriteria criteria);

    /**
     * 销售明细总数
     */
    long countDetailList(@Param("criteria") SalesDashboardQueryCriteria criteria);

    /**
     * 销售渠道汇总卡片
     */
    SalesChannelSummaryVO getChannelSummary(
            @Param("customerSource") String customerSource,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 销售员汇总卡片
     */
    SalesSalespersonSummaryVO getSalespersonSummary(
            @Param("parentPackageId") Long parentPackageId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);
}
