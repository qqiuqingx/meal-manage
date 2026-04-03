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
package me.zhengjie.modules.sales.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.sales.domain.dto.*;
import me.zhengjie.modules.sales.service.SalesDashboardService;
import me.zhengjie.utils.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售看板 Controller
 * @author qqx
 **/
@Api(tags = "销售看板管理")
@RestController
@RequestMapping("/api/sales/dashboard")
@RequiredArgsConstructor
public class SalesDashboardController {

    private final SalesDashboardService salesDashboardService;

    @ApiOperation("获取销售看板金额概览")
    @GetMapping("/overview")
    @PreAuthorize("@el.check('salesDashboard:list')")
    public ResponseEntity<SalesOverviewVO> getOverview(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(salesDashboardService.getOverview(startDate, endDate));
    }

    @ApiOperation("获取月度销售金额趋势")
    @GetMapping("/monthly")
    @PreAuthorize("@el.check('salesDashboard:list')")
    public ResponseEntity<SalesMonthlyTrendVO> getMonthlyTrend(
            @RequestParam Integer year) {
        return ResponseEntity.ok(salesDashboardService.getMonthlyTrend(year));
    }

    @ApiOperation("获取销售看板 TOP3 数据")
    @GetMapping("/top")
    @PreAuthorize("@el.check('salesDashboard:list')")
    public ResponseEntity<SalesDashboardTopVO> getTop(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(salesDashboardService.getTop(startDate, endDate));
    }

    @ApiOperation("获取销售明细表")
    @GetMapping("/detail")
    @PreAuthorize("@el.check('salesDashboard:list')")
    public ResponseEntity<PageResult<SalesDetailVO>> getDetail(
            SalesDashboardQueryCriteria criteria,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(salesDashboardService.getDetail(criteria, page, size));
    }

    @ApiOperation("获取销售渠道汇总卡片")
    @GetMapping("/channel-summary")
    @PreAuthorize("@el.check('salesDashboard:list')")
    public ResponseEntity<SalesChannelSummaryVO> getChannelSummary(
            @RequestParam String customerSource,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(salesDashboardService.getChannelSummary(customerSource, startDate, endDate));
    }

    @ApiOperation("获取销售员汇总卡片")
    @GetMapping("/salesperson-summary")
    @PreAuthorize("@el.check('salesDashboard:list')")
    public ResponseEntity<SalesSalespersonSummaryVO> getSalespersonSummary(
            @RequestParam Long parentPackageId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(salesDashboardService.getSalespersonSummary(parentPackageId, startDate, endDate));
    }
}
