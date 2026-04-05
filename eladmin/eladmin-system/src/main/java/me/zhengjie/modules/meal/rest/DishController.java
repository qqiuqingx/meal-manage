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

import me.zhengjie.annotation.Log;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.service.DishService;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishScheduleResult;
import me.zhengjie.modules.meal.domain.dto.DishScheduleStats;
import me.zhengjie.modules.meal.domain.dto.DishScheduleRecordQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishScheduleRecordVO;
import me.zhengjie.modules.meal.domain.dto.DailyCustomerStats;
import me.zhengjie.modules.customer.pkg.domain.dto.ParentPackageDto;
import me.zhengjie.modules.customer.pkg.service.ParentPackageService;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.annotations.*;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.utils.PageResult;

/**
 * 菜品管理
 * @author qqx
 * @date 2026-03-14
 **/
@RestController
@RequiredArgsConstructor
@Api(tags = "菜品管理")
@RequestMapping("/api/dishes")
public class DishController {

    private final DishService dishService;
    private final ParentPackageService parentPackageService;

    @GetMapping("/packages")
    @ApiOperation("获取套餐选项列表（用于菜品关联）")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<List<ParentPackageDto>> queryPackages(){
        return new ResponseEntity<>(parentPackageService.getTree(), HttpStatus.OK);
    }

    @ApiOperation("导出菜品数据")
    @GetMapping(value = "/download")
    @PreAuthorize("@el.check('dish:list')")
    public void exportDish(HttpServletResponse response, DishQueryCriteria criteria) throws IOException {
        dishService.download(dishService.queryAll(criteria), response);
    }

    @GetMapping
    @ApiOperation("查询菜品")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<PageResult<Dish>> queryDish(DishQueryCriteria criteria){
        Page<Object> page = new Page<>(criteria.getPage() + 1, criteria.getSize());
        return new ResponseEntity<>(dishService.queryAll(criteria,page),HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @ApiOperation("查询菜品详情")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<Dish> queryDishById(@PathVariable Integer id){
        return new ResponseEntity<>(dishService.getById(id),HttpStatus.OK);
    }

    @PostMapping
    @Log("新增菜品")
    @ApiOperation("新增菜品")
    @PreAuthorize("@el.check('dish:add')")
    public ResponseEntity<Object> createDish(@Validated @RequestBody Dish resources){
        dishService.create(resources);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PutMapping
    @Log("修改菜品")
    @ApiOperation("修改菜品")
    @PreAuthorize("@el.check('dish:edit')")
    public ResponseEntity<Object> updateDish(@Validated @RequestBody Dish resources){
        dishService.update(resources);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping
    @Log("删除菜品")
    @ApiOperation("删除菜品")
    @PreAuthorize("@el.check('dish:del')")
    public ResponseEntity<Object> deleteDish(@ApiParam(value = "传ID数组[]") @RequestBody List<Integer> ids) {
        dishService.deleteAll(ids);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/schedule")
    @ApiOperation("按排期查询菜品")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<List<Dish>> queryBySchedule(
            @RequestParam Integer week,
            @RequestParam Integer day,
            @RequestParam(required = false) String mealType){
        return new ResponseEntity<>(dishService.findBySchedule(week, day, mealType),HttpStatus.OK);
    }

    @GetMapping("/available")
    @ApiOperation("获取客户可用菜品（根据忌口过滤）")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<List<Dish>> queryAvailableDishes(
            @RequestParam Integer customerId,
            @RequestParam String mealType,
            @RequestParam(required = false) Integer week,
            @RequestParam(required = false) Integer day){
        return new ResponseEntity<>(dishService.findAvailableByCustomerId(customerId, mealType, week, day),HttpStatus.OK);
    }

    @PostMapping("/schedule/{date}")
    @Log("生成排餐并保存记录")
    @ApiOperation("生成排餐结果并保存记录")
    @PreAuthorize("@el.check('dish:add')")
    public ResponseEntity<DishScheduleResult> generateSchedule(
            @PathVariable String date,
            @RequestParam(required = false, defaultValue = "ALL") String mealType,
            @RequestParam(required = false, defaultValue = "SCHEDULE") String scheduleMode,
            @RequestParam(required = false) Integer customerId){
        return new ResponseEntity<>(dishService.getScheduleAndSave(date, mealType, scheduleMode, customerId),HttpStatus.OK);
    }

    @GetMapping("/schedule/stats/{date}")
    @ApiOperation("获取首页排餐统计数据")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<DishScheduleStats> getScheduleStats(@PathVariable String date){
        return new ResponseEntity<>(dishService.getScheduleStats(date),HttpStatus.OK);
    }

    @GetMapping("/schedule/list")
    @ApiOperation("查询排餐记录列表（分页）")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<PageResult<DishScheduleRecordVO>> queryScheduleRecord(DishScheduleRecordQueryCriteria criteria){
        Page<Object> page = new Page<>(criteria.getPage() + 1, criteria.getSize());
        return new ResponseEntity<>(dishService.queryScheduleRecord(criteria, page),HttpStatus.OK);
    }

    @DeleteMapping("/schedule/{id}")
    @Log("删除排餐记录")
    @ApiOperation("删除排餐记录")
    @PreAuthorize("@el.check('dish:del')")
    public ResponseEntity<Object> deleteSchedule(@PathVariable Long id) {
        dishService.deleteSchedule(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/schedule/customer-stats")
    @ApiOperation("获取当天客户总数按套餐和餐次分组统计")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<DailyCustomerStats> getDailyCustomerStats(
            @RequestParam(required = false) String date) {
        return new ResponseEntity<>(dishService.getDailyCustomerStats(date), HttpStatus.OK);
    }

    @GetMapping("/schedule/customer-source-stats")
    @ApiOperation("获取客户按来源分组统计（支持传入具体日期或月份）")
    @PreAuthorize("@el.check('dish:list')")
    public ResponseEntity<List<Map<String, Object>>> getCustomerSourceStats(
            @RequestParam(required = false) String date) {
        return new ResponseEntity<>(dishService.getCustomerSourceStats(date), HttpStatus.OK);
    }
}
