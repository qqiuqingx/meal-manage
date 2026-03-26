package me.zhengjie.modules.customer.profile.rest;

import me.zhengjie.annotation.Log;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileStatusRequestDto;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.utils.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 客户档案管理 REST 控制器
 */
@RestController
@RequestMapping("/api/customerProfile")
public class CustomerProfileController {

    @Autowired
    private CustomerProfileService profileService;

    /**
     * 分页查询客户档案
     */
    @GetMapping
    @PreAuthorize("@el.check('customerProfile:list')")
    public ResponseEntity<PageResult<CustomerProfile>> query(
            CustomerProfileQueryCriteria criteria,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(profileService.query(criteria, current, size));
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
     * 更新客户状态
     */
    @PutMapping("/{id}/status")
    @Log("更新客户档案状态")
    @PreAuthorize("@el.check('customerProfile:status')")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long id,
            @Validated @RequestBody CustomerProfileStatusRequestDto dto) {
        profileService.updateStatus(id, dto);
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

    // 注意: 本期不开放普通业务 DELETE 接口
}