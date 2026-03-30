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
package me.zhengjie.modules.customer.pkg.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.annotation.Log;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.domain.dto.ParentPackageDto;
import me.zhengjie.modules.customer.pkg.domain.dto.ParentPackageQueryCriteria;
import me.zhengjie.modules.customer.pkg.domain.dto.SubPackageCreateDto;
import me.zhengjie.modules.customer.pkg.domain.dto.SubPackageDto;
import me.zhengjie.modules.customer.pkg.service.ParentPackageService;
import me.zhengjie.modules.customer.pkg.service.SubPackageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 套餐管理 REST 控制器
 * @author qqx
 */
@RestController
@RequestMapping("/api/package")
@RequiredArgsConstructor
public class ParentPackageController {

    private final ParentPackageService parentPackageService;
    private final SubPackageService subPackageService;

    @GetMapping
    @PreAuthorize("@el.check('package:list')")
    public ResponseEntity<Object> query(ParentPackageQueryCriteria criteria) {
        return ResponseEntity.ok(parentPackageService.query(criteria));
    }

    @GetMapping("/tree")
    @PreAuthorize("@el.check('package:list')")
    public ResponseEntity<List<ParentPackageDto>> getTree() {
        return ResponseEntity.ok(parentPackageService.getTree());
    }

    @GetMapping("/parent/{id}")
    @PreAuthorize("@el.check('package:list')")
    public ResponseEntity<ParentPackageDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(parentPackageService.findById(id));
    }

    @PostMapping
    @Log("新增套餐")
    @PreAuthorize("@el.check('package:add')")
    public ResponseEntity<Void> create(@RequestBody ParentPackageDto dto) {
        List<Long> subPackageIds = dto.getChildren() == null ? Collections.<Long>emptyList() :
                dto.getChildren().stream()
                        .map(SubPackageDto::getId)
                        .collect(Collectors.toList());
        ParentPackage entity = new ParentPackage();
        entity.setId(dto.getId());
        entity.setPackageCode(dto.getPackageCode());
        entity.setPrefix(dto.getPrefix());
        entity.setPackageName(dto.getPackageName());
        entity.setStatus(dto.getStatus() != null && dto.getStatus() == 1);
        entity.setRemark(dto.getRemark());
        parentPackageService.create(entity, subPackageIds);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping
    @Log("编辑套餐")
    @PreAuthorize("@el.check('package:edit')")
    public ResponseEntity<Void> update(@RequestBody ParentPackageDto dto) {
        List<Long> subPackageIds = dto.getChildren() == null ? Collections.<Long>emptyList() :
                dto.getChildren().stream()
                        .map(SubPackageDto::getId)
                        .collect(Collectors.toList());
        ParentPackage entity = new ParentPackage();
        entity.setId(dto.getId());
        entity.setPackageCode(dto.getPackageCode());
        entity.setPrefix(dto.getPrefix());
        entity.setPackageName(dto.getPackageName());
        entity.setStatus(dto.getStatus() != null && dto.getStatus() == 1);
        entity.setRemark(dto.getRemark());
        parentPackageService.update(entity, subPackageIds);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/status/{id}")
    @Log("修改套餐状态")
    @PreAuthorize("@el.check('package:status')")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        parentPackageService.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Log("删除套餐")
    @PreAuthorize("@el.check('package:del')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parentPackageService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ===== 子套餐独立操作 =====

    @GetMapping("/sub/{id}")
    public ResponseEntity<SubPackageDto> findSubById(@PathVariable Long id) {
        return ResponseEntity.ok(subPackageService.findById(id));
    }

    @PostMapping("/sub")
    @Log("新增子套餐")
    @PreAuthorize("@el.check('package:add')")
    public ResponseEntity<Void> createSub(@RequestBody SubPackageCreateDto dto) {
        SubPackage subPackage = new SubPackage();
        subPackage.setSubPackageCode(dto.getSubPackageCode());
        subPackage.setSubPackageName(dto.getSubPackageName());
        subPackage.setMeatCount(dto.getMeatCount());
        subPackage.setVegCount(dto.getVegCount());
        subPackage.setIncludeSoup(dto.getIncludeSoup() != null && dto.getIncludeSoup() == 1);
        subPackage.setIncludeRice(dto.getIncludeRice() != null && dto.getIncludeRice() == 1);
        subPackage.setStatus(Boolean.TRUE);
        subPackage.setRemark(dto.getRemark());
        subPackageService.create(subPackage, dto.getParentPackageId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/sub")
    @Log("编辑子套餐")
    @PreAuthorize("@el.check('package:edit')")
    public ResponseEntity<Void> updateSub(@RequestBody SubPackageDto dto) {
        SubPackage resources = new SubPackage();
        resources.setId(dto.getId());
        resources.setSubPackageCode(dto.getSubPackageCode());
        resources.setSubPackageName(dto.getSubPackageName());
        resources.setMeatCount(dto.getMeatCount());
        resources.setVegCount(dto.getVegCount());
        resources.setIncludeSoup(dto.getIncludeSoup() != null && dto.getIncludeSoup() == 1);
        resources.setIncludeRice(dto.getIncludeRice() != null && dto.getIncludeRice() == 1);
        if (dto.getStatus() != null) {
            resources.setStatus(dto.getStatus() == 1);
        }
        resources.setRemark(dto.getRemark());
        subPackageService.update(resources);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/sub/status/{id}")
    @Log("修改子套餐状态")
    @PreAuthorize("@el.check('package:status')")
    public ResponseEntity<Void> updateSubStatus(@PathVariable Long id, @RequestParam Integer status) {
        subPackageService.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/sub/{id}")
    @Log("删除子套餐")
    @PreAuthorize("@el.check('package:del')")
    public ResponseEntity<Void> deleteSub(@PathVariable Long id) {
        subPackageService.delete(id);
        return ResponseEntity.ok().build();
    }
}
