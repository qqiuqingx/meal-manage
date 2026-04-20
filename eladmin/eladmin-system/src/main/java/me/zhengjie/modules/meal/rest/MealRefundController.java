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
package me.zhengjie.modules.meal.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.Log;
import me.zhengjie.modules.meal.domain.MealRefundLog;
import me.zhengjie.modules.meal.domain.dto.MealRefundDto;
import me.zhengjie.modules.meal.domain.dto.MealRefundLogVO;
import me.zhengjie.modules.meal.domain.dto.MealRefundQueryCriteria;
import me.zhengjie.modules.meal.service.MealRefundService;
import me.zhengjie.utils.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退餐管理控制器
 * @author qqx
 * @date 2026-04-19
 */
@RestController
@RequiredArgsConstructor
@Api(tags = "退餐管理")
@RequestMapping("/api/meal-refund")
public class MealRefundController {

    private final MealRefundService mealRefundService;

    /**
     * 执行退餐
     */
    @Log("执行退餐")
    @ApiOperation("执行退餐")
    @PostMapping
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<MealRefundLog> refund(@Validated @RequestBody MealRefundDto dto) {
        MealRefundLog result = mealRefundService.refund(dto);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    /**
     * 分页查询退餐日志
     */
    @Log("查询退餐日志列表")
    @ApiOperation("分页查询退餐日志")
    @GetMapping("/logs")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<PageResult<MealRefundLogVO>> queryRefundLogs(MealRefundQueryCriteria criteria) {
        return new ResponseEntity<>(mealRefundService.queryAll(criteria), HttpStatus.OK);
    }

    /**
     * 根据订单ID查询退餐日志
     */
    @Log("查询订单退餐日志")
    @ApiOperation("根据订单ID查询退餐日志")
    @GetMapping("/logs/order/{orderId}")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<MealRefundLog> queryByOrderId(@PathVariable Long orderId) {
        return new ResponseEntity<>(mealRefundService.queryByOrderId(orderId), HttpStatus.OK);
    }
}
