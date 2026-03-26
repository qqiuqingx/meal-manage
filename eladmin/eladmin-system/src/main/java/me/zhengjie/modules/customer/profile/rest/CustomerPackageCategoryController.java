package me.zhengjie.modules.customer.profile.rest;

import me.zhengjie.annotation.Log;
import me.zhengjie.modules.customer.profile.domain.CustomerPackageCategory;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerPackageCategoryQueryCriteria;
import me.zhengjie.modules.customer.profile.service.CustomerPackageCategoryService;
import me.zhengjie.utils.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 套餐分类管理 REST 控制器
 */
@RestController
@RequestMapping("/api/customerPackageCategory")
public class CustomerPackageCategoryController {

    @Autowired
    private CustomerPackageCategoryService categoryService;

    /**
     * 分页查询分类
     */
    @GetMapping
    @PreAuthorize("@el.check('customerPackageCategory:list')")
    public ResponseEntity<PageResult<CustomerPackageCategory>> query(
            CustomerPackageCategoryQueryCriteria criteria,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(categoryService.query(criteria, page, size));
    }

    /**
     * 获取分类树形结构
     */
    @GetMapping("/tree")
    @PreAuthorize("@el.check('customerPackageCategory:list')")
    public ResponseEntity<List<CustomerPackageCategory>> getTree() {
        return ResponseEntity.ok(categoryService.getTree());
    }

    /**
     * 获取父级分类列表(仅启用状态)
     */
    @GetMapping("/parents")
    @PreAuthorize("@el.check('customerPackageCategory:list')")
    public ResponseEntity<List<CustomerPackageCategory>> getParents() {
        return ResponseEntity.ok(categoryService.getParents());
    }

    /**
     * 新增分类
     */
    @PostMapping
    @Log("新增套餐分类")
    @PreAuthorize("@el.check('customerPackageCategory:add')")
    public ResponseEntity<Void> create(@Validated @RequestBody CustomerPackageCategory category) {
        categoryService.create(category);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 编辑分类
     */
    @PutMapping
    @Log("编辑套餐分类")
    @PreAuthorize("@el.check('customerPackageCategory:edit')")
    public ResponseEntity<Void> update(@Validated @RequestBody CustomerPackageCategory category) {
        categoryService.update(category);
        return ResponseEntity.ok().build();
    }

    /**
     * 更新分类状态
     */
    @PutMapping("/{id}/status")
    @Log("更新套餐分类状态")
    @PreAuthorize("@el.check('customerPackageCategory:status')")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody StatusRequest request) {
        categoryService.updateStatus(id, request.getEnabled());
        return ResponseEntity.ok().build();
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    @Log("删除套餐分类")
    @PreAuthorize("@el.check('customerPackageCategory:del')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 状态更新请求体
     */
    static class StatusRequest {
        private Boolean enabled;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}