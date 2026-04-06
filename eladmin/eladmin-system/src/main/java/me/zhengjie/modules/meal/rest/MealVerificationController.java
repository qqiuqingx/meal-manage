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
import me.zhengjie.modules.meal.domain.MealVerificationLog;
import me.zhengjie.modules.meal.domain.dto.MealVerificationDto;
import me.zhengjie.modules.meal.domain.dto.MealVerificationResultDto;
import me.zhengjie.modules.meal.service.MealVerificationService;
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

import java.util.List;

/**
 * 核销管理控制器
 * @author qqx
 * @date 2026-04-05
 **/
@RestController
@RequiredArgsConstructor
@Api(tags = "核销管理")
@RequestMapping("/api/meal-verification")
public class MealVerificationController {

    private final MealVerificationService mealVerificationService;

    /**
     * 执行核销
     */
    @Log("执行核销: 客户数={#dto.customerPlanIds.size()}, 备注={#dto.remark}")
    @ApiOperation("执行核销")
    @PostMapping("/verify")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<MealVerificationResultDto> verify(@Validated @RequestBody MealVerificationDto dto) {
        return new ResponseEntity<>(mealVerificationService.verify(dto), HttpStatus.OK);
    }

    /**
     * 根据ID查询核销日志
     */
    @Log("查询核销日志")
    @ApiOperation("查询核销日志")
    @GetMapping("/logs/{id}")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<MealVerificationLog> queryLogById(@PathVariable Long id) {
        return new ResponseEntity<>(mealVerificationService.queryById(id), HttpStatus.OK);
    }

    /**
     * 根据订单ID查询核销日志列表
     */
    @Log("查询订单核销记录")
    @ApiOperation("查询订单核销记录")
    @GetMapping("/logs/order/{orderId}")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<List<MealVerificationLog>> queryLogsByOrderId(@PathVariable Long orderId) {
        return new ResponseEntity<>(mealVerificationService.queryByOrderId(orderId), HttpStatus.OK);
    }
}
