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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

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
     * 条件查询客户档案列表（分页）
     */
    IPage<CustomerProfile> findAll(@Param("criteria") CustomerProfileQueryCriteria criteria, Page<Object> page);

    /**
     * 根据客户编号查询(排除指定ID)
     */
    int countByCodeExcludeId(@Param("customerCode") String customerCode, @Param("excludeId") Long excludeId);

    /**
     * 批量查询客户档案
     */
    List<CustomerProfile> findByIds(@Param("ids") Set<Long> ids);

    /**
     * 根据ID查询（带JSON字段）
     */
    CustomerProfile selectByIdWithJson(@Param("id") Long id);

    /**
     * 锁定客户档案行，供订单行内共享字段或客户编号更新使用。
     *
     * @param id 客户主键
     * @return 当前客户档案；不存在时返回 null
     */
    CustomerProfile selectByIdForInlineUpdate(@Param("id") Long id);

    /**
     * 锁定客户主档行，供批量导入补录时复核身份并合并共享客户信息。
     *
     * @param id 客户主键
     * @return 锁定后的客户档案；不存在时返回 null
     */
    CustomerProfile selectByIdForImportUpdate(@Param("id") Long id);

    /**
     * 定向更新客户过敏标签，并记录最后修改人。
     *
     * @param id 客户主键
     * @param allergyTagsJson 规范化后的 JSON 数组文本
     * @param updateBy 最后修改人
     * @return 更新行数
     */
    int updateAllergyTagsInline(@Param("id") Long id,
                                @Param("allergyTagsJson") String allergyTagsJson,
                                @Param("updateBy") String updateBy,
                                @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 定向更新客户菜品特殊要求对象引用 JSON。
     *
     * @param id 客户主键
     * @param json 规范化后的对象引用 JSON 数组
     * @param updateBy 最后修改人
     * @param updateTime 最后修改时间
     * @return 更新行数
     */
    int updateDishRequirementsInline(@Param("id") Long id,
                                     @Param("json") String json,
                                     @Param("updateBy") String updateBy,
                                     @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 定向更新客户禁忌对象引用 JSON。
     *
     * @param id 客户主键
     * @param json 规范化后的对象引用 JSON 数组
     * @param updateBy 最后修改人
     * @param updateTime 最后修改时间
     * @return 更新行数
     */
    int updateDietaryRestrictionsInline(@Param("id") Long id,
                                        @Param("json") String json,
                                        @Param("updateBy") String updateBy,
                                        @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 定向更新客户特殊要求，并记录最后修改人。
     *
     * @param id 客户主键
     * @param specialRequirements 特殊要求；null 表示清空
     * @param updateBy 最后修改人
     * @return 更新行数
     */
    int updateSpecialRequirementsInline(@Param("id") Long id,
                                        @Param("specialRequirements") String specialRequirements,
                                        @Param("updateBy") String updateBy,
                                        @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 行内更新客户手机号并记录修改人。
     *
     * @param id 客户主键
     * @param phone 新手机号
     * @param updateBy 操作人
     * @param updateTime 修改时间
     * @return 更新行数
     */
    int updatePhoneInline(@Param("id") Long id,
                          @Param("phone") String phone,
                          @Param("updateBy") String updateBy,
                          @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 定向更新客户编号，并记录最后修改人。
     *
     * @param id 客户主键
     * @param customerCode 新客户编号
     * @param updateBy 最后修改人
     * @return 更新行数
     */
    int updateCustomerCodeInline(@Param("id") Long id,
                                 @Param("customerCode") String customerCode,
                                 @Param("updateBy") String updateBy,
                                 @Param("updateTime") java.time.LocalDateTime updateTime);
}
