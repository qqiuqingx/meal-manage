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
package me.zhengjie.modules.customer.pkg.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.pkg.domain.ParentPackageSub;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.domain.dto.SubPackageDto;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageSubMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.pkg.service.SubPackageService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 子套餐服务实现
 * @author qqx
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class SubPackageServiceImpl implements SubPackageService {

    private final SubPackageMapper subPackageMapper;
    private final ParentPackageSubMapper parentPackageSubMapper;

    @Override
    public SubPackageDto findById(Long id) {
        SubPackage entity = subPackageMapper.selectById(id);
        if (entity == null) {
            throw new BadRequestException("子套餐不存在");
        }
        return toDto(entity);
    }

    @Override
    public void create(SubPackage resources, Long parentPackageId) {
        subPackageMapper.insert(resources);

        // 插入父子关联记录
        ParentPackageSub relation = new ParentPackageSub();
        relation.setParentPackageId(parentPackageId);
        relation.setSubPackageId(resources.getId());
        relation.setStatus(1);
        relation.setCreatedAt(LocalDateTime.now());
        parentPackageSubMapper.insert(relation);
    }

    @Override
    public void update(SubPackage resources) {
        subPackageMapper.updateById(resources);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SubPackage entity = subPackageMapper.selectById(id);
        if (entity == null) {
            throw new BadRequestException("子套餐不存在");
        }
        entity.setStatus(status != null && status == 1);
        subPackageMapper.updateById(entity);
    }

    /**
     * 删除子套餐。
     *
     * @param id 子套餐ID，仅用于校验订单的 child_package_id 引用并删除对应子套餐
     */
    @Override
    public void delete(Long id) {
        SubPackage entity = subPackageMapper.selectById(id);
        if (entity == null) {
            throw new BadRequestException("子套餐不存在");
        }

        // 检查是否被订单引用（child_package_id）
        Integer orderCountBySubId = subPackageMapper.countOrderBySubPackageId(id);
        if (orderCountBySubId != null && orderCountBySubId > 0) {
            throw new BadRequestException("该子套餐已被订单引用，无法删除");
        }

        // 删除关联记录
        parentPackageSubMapper.deleteBySubPackageId(id);
        // 删除子套餐
        subPackageMapper.deleteById(id);
    }

    private SubPackageDto toDto(SubPackage entity) {
        SubPackageDto dto = new SubPackageDto();
        BeanUtils.copyProperties(entity, dto);
        dto.setStatus(entity.getStatus() != null && entity.getStatus() ? 1 : 0);
        dto.setIncludeSoup(entity.getIncludeSoup() != null && entity.getIncludeSoup() ? 1 : 0);
        dto.setIncludeRice(entity.getIncludeRice() != null && entity.getIncludeRice() ? 1 : 0);
        return dto;
    }
}
