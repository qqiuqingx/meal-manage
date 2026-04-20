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
package me.zhengjie.modules.meal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.meal.domain.MealVerificationLog;
import me.zhengjie.modules.meal.domain.dto.MealVerificationLogQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealVerificationLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 核销日志Mapper
 * @author qqx
 * @date 2026-04-05
 **/
@Mapper
public interface MealVerificationLogMapper extends BaseMapper<MealVerificationLog> {

    /**
     * 分页查询核销记录
     * @param criteria 查询条件
     * @param page     分页参数
     * @return 核销记录分页结果
     */
    Page<MealVerificationLogVO> selectPageByCriteria(@Param("criteria") MealVerificationLogQueryCriteria criteria,
                                                      Page<MealVerificationLogVO> page);

    /**
     * 仅软删除未删除的核销日志，避免重复删除导致重复回滚
     */
    int softDeleteIfActive(@Param("id") Long id,
                           @Param("deletedBy") String deletedBy,
                           @Param("deleteTime") Date deleteTime);

    /**
     * 标记核销记录为已退餐
     * @param orderId 订单ID
     * @return 更新行数
     */
    int markAsRefunded(@Param("orderId") Long orderId);
}
