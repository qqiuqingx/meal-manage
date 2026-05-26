package me.zhengjie.modules.customer.profile.rest;

import me.zhengjie.annotation.Log;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentRequest;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentResult;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsRowDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseRequest;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerIntakeParseResult;
import me.zhengjie.modules.customer.profile.service.CustomerIntakeParseService;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.utils.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 客户档案管理 REST 控制器
 */
@RestController
@RequestMapping("/api/customerProfile")
public class CustomerProfileController {

    @Autowired
    private CustomerProfileService profileService;

    @Autowired
    private CustomerIntakeParseService intakeParseService;

    /**
     * 分页查询客户档案
     */
    @GetMapping
    @PreAuthorize("@el.check('customerProfile:list')")
    public ResponseEntity<PageResult<CustomerProfile>> queryAll( CustomerProfileQueryCriteria criteria,
                                                                 @RequestParam(defaultValue = "1") Integer page,
                                                                 @RequestParam(defaultValue = "10") Integer size) {
        Page<Object> page1 = new Page<>(page, size);
        return ResponseEntity.ok(profileService.queryAll(criteria, page1));
    }

    /**
     * 分页查询客户用餐统计
     */
    @GetMapping("/mealStats")
    @PreAuthorize("@el.check('customerProfile:list')")
    public ResponseEntity<PageResult<CustomerMealStatsRowDto>> queryMealStats(CustomerMealStatsQueryCriteria criteria,
                                                                              @RequestParam(defaultValue = "1") Integer page,
                                                                              @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(profileService.queryMealStats(criteria, page, size));
    }

    /**
     * 保存客户排餐日历调整
     */
    @PutMapping("/mealStats/scheduleAdjustments")
    @Log("保存客户排餐日历调整")
    @PreAuthorize("@el.check('customerProfile:edit')")
    public ResponseEntity<CustomerMealScheduleAdjustmentResult> saveMealScheduleAdjustments(
            @Validated @RequestBody CustomerMealScheduleAdjustmentRequest request) {
        return ResponseEntity.ok(profileService.saveMealScheduleAdjustments(request));
    }

    /**
     * 获取客户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@el.check('customerProfile:list')")
    public ResponseEntity<CustomerProfileDetailDto> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(profileService.getDetail(id));
    }

    /**
     * 新增客户档案
     */
    @PostMapping
    @Log("新增客户档案")
    @PreAuthorize("@el.check('customerProfile:add')")
    public ResponseEntity<Void> create(@Validated @RequestBody CustomerProfileSaveDto dto) {
        profileService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 解析客户建档话术。
     */
    @PostMapping("/intake/parse")
    @PreAuthorize("@el.check('customerProfile:add')")
    public ResponseEntity<CustomerIntakeParseResult> parseIntakeText(@RequestBody CustomerIntakeParseRequest request) {
        return ResponseEntity.ok(intakeParseService.parse(request));
    }

    /**
     * 编辑客户档案
     */
    @PutMapping
    @Log("编辑客户档案")
    @PreAuthorize("@el.check('customerProfile:edit')")
    public ResponseEntity<Void> update(@Validated @RequestBody CustomerProfileSaveDto dto) {
        profileService.update(dto);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量删除客户档案
     */
    @DeleteMapping
    @Log("删除客户档案")
    @PreAuthorize("@el.check('customerProfile:del')")
    public ResponseEntity<Void> delete(@RequestBody Set<Long> ids) {
        profileService.delete(ids);
        return ResponseEntity.ok().build();
    }

    /**
     * 生成客户编号
     */
    @GetMapping("/generateCode")
    @PreAuthorize("@el.check('customerProfile:add')")
    public ResponseEntity<String> generateCode(@RequestParam Long parentPackageId) {
        return ResponseEntity.ok(profileService.generateCode(parentPackageId));
    }
}
