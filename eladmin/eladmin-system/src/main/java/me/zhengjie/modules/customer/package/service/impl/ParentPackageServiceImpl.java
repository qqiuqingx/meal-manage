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
package me.zhengjie.modules.customer.package.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.package.domain.ParentPackage;
import me.zhengjie.modules.customer.package.domain.ParentPackageSub;
import me.zhengjie.modules.customer.package.domain.SubPackage;
import me.zhengjie.modules.customer.package.domain.dto.ParentPackageDto;
import me.zhengjie.modules.customer.package.domain.dto.ParentPackageQueryCriteria;
import me.zhengjie.modules.customer.package.domain.dto.SubPackageDto;
import me.zhengjie.modules.customer.package.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.package.mapper.ParentPackageSubMapper;
import me.zhengjie.modules.customer.package.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.package.service.ParentPackageService;
import me.zhengjie.utils.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 父套餐服务实现
 * @author qqx
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ParentPackageServiceImpl implements ParentPackageService {

    private final ParentPackageMapper parentPackageMapper;
    private final SubPackageMapper subPackageMapper;
    private final ParentPackageSubMapper parentPackageSubMapper;

    @Override
    public PageResult<ParentPackageDto> query(ParentPackageQueryCriteria criteria) {
        List<ParentPackage> list = parentPackageMapper.queryList(criteria);
        List<ParentPackageDto> dtoList = list.stream().map(this::toDto).collect(Collectors.toList());
        return new PageResult<>(dtoList, dtoList.size());
    }

    @Override
    public List<ParentPackageDto> getTree() {
        List<ParentPackage> all = parentPackageMapper.queryList(null);
        return all.stream().map(parent -> {
            ParentPackageDto dto = toDto(parent);
            List<SubPackage> subPackages = subPackageMapper.findByParentPackageId(parent.getId());
            dto.setChildren(subPackages.stream().map(this::toSubDto).collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public ParentPackageDto findById(Long id) {
        ParentPackage parent = parentPackageMapper.selectById(id);
        if (parent == null) {
            throw new BadRequestException("父套餐不存在");
        }
        ParentPackageDto dto = toDto(parent);
        List<SubPackage> subPackages = subPackageMapper.findByParentPackageId(id);
        dto.setChildren(subPackages.stream().map(this::toSubDto).collect(Collectors.toList()));
        return dto;
    }

    @Override
    public void create(ParentPackage resources, List<Long> subPackageIds) {
        // prefix 唯一性校验
        if (parentPackageMapper.existsByPrefix(resources.getPrefix(), null)) {
            throw new BadRequestException("编号前缀 " + resources.getPrefix() + " 已存在");
        }
        parentPackageMapper.insert(resources);
        insertSubRelations(resources.getId(), subPackageIds);
    }

    @Override
    public void update(ParentPackage resources, List<Long> subPackageIds) {
        // prefix 唯一性校验（排除自身）
        if (parentPackageMapper.existsByPrefix(resources.getPrefix(), resources.getId())) {
            throw new BadRequestException("编号前缀 " + resources.getPrefix() + " 已存在");
        }
        parentPackageMapper.updateById(resources);
        // 先删旧关联，再批量插入新关联
        parentPackageSubMapper.deleteByParentPackageId(resources.getId());
        insertSubRelations(resources.getId(), subPackageIds);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        ParentPackage parent = parentPackageMapper.selectById(id);
        if (parent == null) {
            throw new BadRequestException("父套餐不存在");
        }
        // 停用时检查是否有启用状态的子套餐关联
        if (status == 0) {
            List<ParentPackageSub> relations = parentPackageSubMapper.findByParentPackageId(id);
            boolean hasEnabledRelation = relations.stream()
                .anyMatch(r -> Objects.equals(r.getStatus(), 1));
            if (hasEnabledRelation) {
                throw new BadRequestException("请先停用所有子套餐关联");
            }
        }
        parent.setStatus(status == 1);
        parentPackageMapper.updateById(parent);
    }

    @Override
    public void delete(Long id) {
        ParentPackage parent = parentPackageMapper.selectById(id);
        if (parent == null) {
            throw new BadRequestException("父套餐不存在");
        }
        // 检查是否有子套餐关联
        Long count = parentPackageMapper.countSubPackagesByParentId(id);
        if (count != null && count > 0) {
            throw new BadRequestException("请先删除所有子套餐关联");
        }
        parentPackageSubMapper.deleteByParentPackageId(id);
        parentPackageMapper.deleteById(id);
    }

    /**
     * 批量插入父子关联记录
     */
    private void insertSubRelations(Long parentPackageId, List<Long> subPackageIds) {
        if (CollectionUtils.isEmpty(subPackageIds)) {
            return;
        }
        List<ParentPackageSub> records = subPackageIds.stream().map(subId -> {
            ParentPackageSub record = new ParentPackageSub();
            record.setParentPackageId(parentPackageId);
            record.setSubPackageId(subId);
            record.setStatus(1);
            record.setCreatedAt(LocalDateTime.now());
            return record;
        }).collect(Collectors.toList());
        parentPackageSubMapper.batchInsert(records);
    }

    private ParentPackageDto toDto(ParentPackage entity) {
        ParentPackageDto dto = new ParentPackageDto();
        BeanUtils.copyProperties(entity, dto);
        dto.setStatus(entity.getStatus() != null && entity.getStatus() ? 1 : 0);
        dto.setChildren(new ArrayList<>());
        return dto;
    }

    private SubPackageDto toSubDto(SubPackage entity) {
        SubPackageDto dto = new SubPackageDto();
        BeanUtils.copyProperties(entity, dto);
        dto.setStatus(entity.getStatus() != null && entity.getStatus() ? 1 : 0);
        dto.setIncludeSoup(entity.getIncludeSoup() != null && entity.getIncludeSoup() ? 1 : 0);
        dto.setIncludeRice(entity.getIncludeRice() != null && entity.getIncludeRice() ? 1 : 0);
        return dto;
    }
}
