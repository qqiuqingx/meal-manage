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
package me.zhengjie.modules.customer.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户档案 Mapper 接口
 */
@Mapper
public interface CustomerProfileMapper extends BaseMapper<CustomerProfile> {

    /**
     * 条件查询客户档案列表
     */
    List<CustomerProfile> findAll(@Param("criteria") CustomerProfileQueryCriteria criteria);

    /**
     * 根据客户编号查询(排除指定ID)
     */
    int countByCodeExcludeId(@Param("customerCode") String customerCode, @Param("excludeId") Long excludeId);

    /**
     * 根据ID查询（带JSON字段）
     */
    CustomerProfile selectByIdWithJson(@Param("id") Long id);
}