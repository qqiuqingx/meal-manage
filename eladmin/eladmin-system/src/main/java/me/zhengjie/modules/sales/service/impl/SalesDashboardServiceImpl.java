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
package me.zhengjie.modules.sales.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.sales.domain.dto.*;
import me.zhengjie.modules.sales.mapper.SalesDashboardMapper;
import me.zhengjie.modules.sales.service.SalesDashboardService;
import me.zhengjie.utils.PageResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售看板 Service 实现
 * @author qqx
 **/
@Service
@RequiredArgsConstructor
public class SalesDashboardServiceImpl implements SalesDashboardService {

    private final SalesDashboardMapper mapper;

    @Override
    public SalesOverviewVO getOverview(String startDate, String endDate) {
        SalesOverviewVO vo = mapper.getOverview(startDate, endDate);
        if (vo == null) {
            vo = new SalesOverviewVO();
            vo.setTodayAmount(BigDecimal.ZERO);
            vo.setWeekAmount(BigDecimal.ZERO);
            vo.setMonthAmount(BigDecimal.ZERO);
            vo.setTotalAmount(BigDecimal.ZERO);
        } else {
            if (vo.getTodayAmount() == null) { vo.setTodayAmount(BigDecimal.ZERO); }
            if (vo.getWeekAmount() == null) { vo.setWeekAmount(BigDecimal.ZERO); }
            if (vo.getMonthAmount() == null) { vo.setMonthAmount(BigDecimal.ZERO); }
            if (vo.getTotalAmount() == null) { vo.setTotalAmount(BigDecimal.ZERO); }
        }
        return vo;
    }

    @Override
    public SalesMonthlyTrendVO getMonthlyTrend(Integer year) {
        List<SalesMonthlyItemVO> raw = mapper.getMonthlyTrend(year);
        // 将已有月份数据存入 Map，缺失月份补 0
        Map<Integer, BigDecimal> monthMap = new HashMap<>();
        for (SalesMonthlyItemVO item : raw) {
            monthMap.put(item.getMonth(), item.getAmount());
        }
        List<SalesMonthlyItemVO> months = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            months.add(new SalesMonthlyItemVO(m, monthMap.getOrDefault(m, BigDecimal.ZERO)));
        }
        SalesMonthlyTrendVO vo = new SalesMonthlyTrendVO();
        vo.setYear(year);
        vo.setMonths(months);
        return vo;
    }

    @Override
    public SalesDashboardTopVO getTop(String startDate, String endDate) {
        SalesDashboardTopVO vo = new SalesDashboardTopVO();
        vo.setProductQuantityTop3(safeList(mapper.getProductQuantityTop3(startDate, endDate)));
        vo.setProductAmountTop3(safeList(mapper.getProductAmountTop3(startDate, endDate)));
        vo.setSalespersonTop3(safeList(mapper.getSalespersonTop3(startDate, endDate)));
        vo.setChannelTop3(safeList(mapper.getChannelTop3(startDate, endDate)));
        return vo;
    }

    @Override
    public PageResult<SalesDetailVO> getDetail(SalesDashboardQueryCriteria criteria, Integer page, Integer size) {
        // 前端 1 基 -> offset
        criteria.setOffset((page - 1) * size);
        criteria.setSize(size);
        List<SalesDetailVO> list = mapper.getDetailList(criteria);
        long total = mapper.countDetailList(criteria);
        return new PageResult<>(list, total);
    }

    @Override
    public SalesChannelSummaryVO getChannelSummary(String customerSource, String startDate, String endDate) {
        SalesChannelSummaryVO vo = mapper.getChannelSummary(customerSource, startDate, endDate);
        if (vo == null) {
            vo = new SalesChannelSummaryVO();
            vo.setChannelName(customerSource);
            vo.setOrderCount(0);
            vo.setSaleAmount(BigDecimal.ZERO);
        } else {
            if (vo.getOrderCount() == null) { vo.setOrderCount(0); }
            if (vo.getSaleAmount() == null) { vo.setSaleAmount(BigDecimal.ZERO); }
        }
        return vo;
    }

    @Override
    public SalesSalespersonSummaryVO getSalespersonSummary(Long parentPackageId, String startDate, String endDate) {
        SalesSalespersonSummaryVO vo = mapper.getSalespersonSummary(parentPackageId, startDate, endDate);
        if (vo == null) {
            vo = new SalesSalespersonSummaryVO();
            vo.setOrderCount(0);
            vo.setSaleAmount(BigDecimal.ZERO);
        } else {
            if (vo.getOrderCount() == null) { vo.setOrderCount(0); }
            if (vo.getSaleAmount() == null) { vo.setSaleAmount(BigDecimal.ZERO); }
        }
        return vo;
    }

    private List<SalesTopItemVO> safeList(List<SalesTopItemVO> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
