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
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.Limit;
import me.zhengjie.annotation.Log;
import me.zhengjie.aspect.LimitType;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerItemVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateRequest;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.domain.dto.MealPlanListDetailVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanQueryCriteria;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.utils.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排餐计划管理
 * @author qqx
 * @date 2026-03-31
 **/
@RestController
@RequiredArgsConstructor
@Api(tags = "排餐计划管理")
@RequestMapping("/api/meal-plan")
public class MealPlanController {

    private final MealPlanService mealPlanService;

    /**
     * 生成指定日期、指定餐次的排餐计划。
     */
    @Log("生成排餐计划")
    @ApiOperation("生成排餐计划")
    @PostMapping("/generate")
    @Limit(key = "generate", period = 60, count = 5, name = "generateMealPlan", prefix = "mealPlan", limitType = LimitType.IP)
    @PreAuthorize("@el.check('mealPlan:generate')")
    public ResponseEntity<MealPlanGenerateResult> generateMealPlan(@Validated @RequestBody MealPlanGenerateRequest request) {
        return new ResponseEntity<>(mealPlanService.generateMealPlan(request.getRecordDate(), request.getMealType(), request.getCustomerId()), HttpStatus.OK);
    }

    /**
     * 分页查询排餐计划列表。
     */
    @Log("查询排餐计划列表")
    @ApiOperation("查询排餐计划列表")
    @GetMapping
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<?> queryMealPlan(
            MealPlanQueryCriteria criteria,
            @ApiParam(value = "是否包含客户和菜品详情") @RequestParam(required = false, defaultValue = "false") Boolean includeDetail) {
        if (Boolean.TRUE.equals(includeDetail)) {
            PageResult<MealPlanListDetailVO> result = mealPlanService.queryAllWithDetail(criteria);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
        return new ResponseEntity<>(mealPlanService.queryAll(criteria), HttpStatus.OK);
    }

    /**
     * 按日期统计各父套餐餐数
     */
    @Log("查询父套餐餐数统计")
    @ApiOperation("按日期统计各父套餐餐数")
    @GetMapping("/statistics-by-date")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<List<MealPackageStatDto>> statByDate(
            @ApiParam(value = "排餐日期，格式 yyyy-MM-dd", required = true) @RequestParam String date) {
        return new ResponseEntity<>(mealPlanService.statByDate(date), HttpStatus.OK);
    }

    /**
     * 根据ID查询排餐计划详情。
     */
    @Log("查询排餐计划详情")
    @ApiOperation("查询排餐计划详情")
    @GetMapping("/{id}")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<MealPlan> queryMealPlanById(@ApiParam(value = "排餐计划ID", required = true) @PathVariable Long id) {
        return new ResponseEntity<>(mealPlanService.queryById(id), HttpStatus.OK);
    }

    /**
     * 根据排餐计划ID查询完整详情（含客户列表和菜品明细）。
     * 一次返回所有数据，无需多次调用接口。
     */
    @Log("查询排餐计划完整详情")
    @ApiOperation("查询排餐计划完整详情（含客户列表和菜品明细）")
    @GetMapping("/{mealPlanId}/detail")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<MealPlanDetailVO> queryMealPlanDetail(@ApiParam(value = "排餐计划ID", required = true) @PathVariable Long mealPlanId) {
        return new ResponseEntity<>(mealPlanService.queryMealPlanDetail(mealPlanId), HttpStatus.OK);
    }

    /**
     * 根据排餐计划ID查询客户列表。
     */
    @Log("查询排餐计划客户列表")
    @ApiOperation("查询排餐计划客户列表")
    @GetMapping("/{mealPlanId}/customers")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<PageResult<MealPlanCustomer>> queryMealPlanCustomers(
            @ApiParam(value = "排餐计划ID", required = true) @PathVariable Long mealPlanId,
            MealPlanCustomerQueryCriteria criteria) {
        criteria.setMealPlanId(mealPlanId);
        return new ResponseEntity<>(mealPlanService.queryCustomers(criteria), HttpStatus.OK);
    }

    /**
     * 根据客户计划ID查询详情。
     */
    @Log("查询排餐计划客户详情")
    @ApiOperation("查询排餐计划客户详情")
    @GetMapping("/customers/{id}")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<MealPlanCustomer> queryMealPlanCustomerById(@ApiParam(value = "客户计划ID", required = true) @PathVariable Long id) {
        return new ResponseEntity<>(mealPlanService.queryCustomerById(id), HttpStatus.OK);
    }

    /**
     * 根据客户计划ID查询菜品明细。
     */
    @Log("查询排餐计划菜品明细")
    @ApiOperation("查询排餐计划菜品明细")
    @GetMapping("/customers/{customerPlanId}/items")
    @PreAuthorize("@el.check('mealPlan:list')")
    public ResponseEntity<List<MealPlanCustomerItemVO>> queryMealPlanCustomerItems(
            @ApiParam(value = "客户计划ID", required = true) @PathVariable Long customerPlanId) {
        return new ResponseEntity<>(mealPlanService.queryCustomerItems(customerPlanId), HttpStatus.OK);
    }

    /**
     * 根据日期和餐次删除排餐计划（级联删除客户和明细）。
     */
    @Log("删除排餐计划")
    @ApiOperation("删除排餐计划")
    @DeleteMapping
    @PreAuthorize("@el.check('mealPlan:del')")
    public ResponseEntity<Void> deleteMealPlan(
            @ApiParam(value = "排餐日期，格式 yyyy-MM-dd", required = true) @RequestParam String recordDate,
            @ApiParam(value = "餐次（LUNCH午餐/DINNER晚餐）", required = true) @RequestParam String mealType,
            @ApiParam(value = "指定客户ID，不传则删除全部") @RequestParam(required = false) Long customerId) {
        mealPlanService.deleteMealPlan(recordDate, mealType, customerId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * 根据ID列表批量删除排餐计划（级联删除客户和明细）。
     */
    @Log("批量删除排餐计划")
    @ApiOperation("批量删除排餐计划")
    @DeleteMapping("/batch")
    @PreAuthorize("@el.check('mealPlan:del')")
    public ResponseEntity<Void> deleteMealPlans(@RequestBody List<Long> ids) {
        mealPlanService.deleteMealPlans(ids);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * 根据客户计划ID列表批量删除客户排餐计划（级联删除明细）。
     */
    @Log("批量删除客户排餐计划")
    @ApiOperation("批量删除客户排餐计划")
    @DeleteMapping("/customers")
    @PreAuthorize("@el.check('mealPlan:del')")
    public ResponseEntity<Void> deleteMealPlanCustomers(@RequestBody List<Long> customerPlanIds) {
        mealPlanService.deleteMealPlanCustomers(customerPlanIds);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
