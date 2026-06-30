package me.zhengjie.modules.meal.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.Log;
import me.zhengjie.modules.meal.domain.MealSchedulePlan;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanBatchResult;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanCopyRequest;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanSaveRequest;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanUpdateRequest;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanWeekVO;
import me.zhengjie.modules.meal.service.MealSchedulePlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排餐坑位管理
 * @author qqx
 * @date 2026-04-13
 **/
@RestController
@RequiredArgsConstructor
@Api(tags = "排餐坑位管理")
@RequestMapping("/api/meal-schedule")
public class MealSchedulePlanController {

    private final MealSchedulePlanService mealSchedulePlanService;

    @GetMapping
    @ApiOperation("查询指定周排餐网格")
    @PreAuthorize("@el.check('mealSchedule:list')")
    public ResponseEntity<MealSchedulePlanWeekVO> queryWeek(@Validated MealSchedulePlanQueryCriteria criteria) {
        return new ResponseEntity<>(mealSchedulePlanService.queryWeek(criteria), HttpStatus.OK);
    }

    @PostMapping
    @Log("新增排餐坑位")
    @ApiOperation("落位菜品到坑位")
    @PreAuthorize("@el.check('mealSchedule:add')")
    public ResponseEntity<MealSchedulePlan> create(@Validated @RequestBody MealSchedulePlanSaveRequest request) {
        return new ResponseEntity<>(mealSchedulePlanService.create(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Log("修改排餐坑位")
    @ApiOperation("更新坑位菜品")
    @PreAuthorize("@el.check('mealSchedule:edit')")
    public ResponseEntity<MealSchedulePlan> update(
            @ApiParam(value = "排餐坑位ID", required = true) @PathVariable Long id,
            @Validated @RequestBody MealSchedulePlanUpdateRequest request) {
        return new ResponseEntity<>(mealSchedulePlanService.update(id, request), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @Log("删除排餐坑位")
    @ApiOperation("移出坑位菜品")
    @PreAuthorize("@el.check('mealSchedule:del')")
    public ResponseEntity<Void> delete(@ApiParam(value = "排餐坑位ID", required = true) @PathVariable Long id) {
        mealSchedulePlanService.delete(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/batch")
    @Log("批量新增排餐坑位")
    @ApiOperation("批量落位菜品")
    @PreAuthorize("@el.check('mealSchedule:add')")
    public ResponseEntity<MealSchedulePlanBatchResult> batchCreate(@Validated @RequestBody List<MealSchedulePlanSaveRequest> requests) {
        return new ResponseEntity<>(mealSchedulePlanService.batchCreate(requests), HttpStatus.CREATED);
    }

    @PostMapping("/copy")
    @Log("复制排餐周")
    @ApiOperation("复制上周排餐")
    @PreAuthorize("@el.check('mealSchedule:add')")
    public ResponseEntity<MealSchedulePlanBatchResult> copyWeek(@Validated @RequestBody MealSchedulePlanCopyRequest request) {
        return new ResponseEntity<>(mealSchedulePlanService.copyWeek(request), HttpStatus.CREATED);
    }
}
