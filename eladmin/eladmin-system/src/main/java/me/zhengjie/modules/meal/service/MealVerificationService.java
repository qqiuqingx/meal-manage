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
package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.MealVerificationLog;
import me.zhengjie.modules.meal.domain.dto.MealVerificationDto;
import me.zhengjie.modules.meal.domain.dto.MealVerificationLogQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealVerificationLogVO;
import me.zhengjie.modules.meal.domain.dto.MealVerificationResultDto;
import me.zhengjie.utils.PageResult;

import java.util.List;

/**
 * 核销服务接口
 * @author qqx
 * @date 2026-04-05
 **/
public interface MealVerificationService {

    /**
     * 执行核销
     * @param dto 核销请求DTO
     * @return 核销结果
     */
    MealVerificationResultDto verify(MealVerificationDto dto);

    /**
     * 执行核销（支持指定操作人）
     * @param dto 核销请求DTO
     * @param operator 操作人，如果为空则从 SecurityContext 获取
     * @return 核销结果
     */
    MealVerificationResultDto verify(MealVerificationDto dto, String operator);

    /**
     * 分页查询核销记录
     * @param criteria 查询条件
     * @return 分页结果
     */
    PageResult<MealVerificationLogVO> queryAll(MealVerificationLogQueryCriteria criteria);

    /**
     * 根据ID查询核销日志
     * @param id 核销日志ID
     * @return 核销日志
     */
    MealVerificationLog queryById(Long id);

    /**
     * 根据订单ID查询核销日志列表
     * @param orderId 订单ID
     * @return 核销日志列表
     */
    List<MealVerificationLog> queryByOrderId(Long orderId);

    /**
     * 删除核销日志（软删除）
     * @param id 核销日志ID
     * @param reason 删除原因
     */
    void deleteVerificationLog(Long id, String reason);
}
