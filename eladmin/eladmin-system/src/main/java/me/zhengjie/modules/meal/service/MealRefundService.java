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

import me.zhengjie.modules.meal.domain.MealRefundLog;
import me.zhengjie.modules.meal.domain.dto.MealRefundDto;
import me.zhengjie.modules.meal.domain.dto.MealRefundLogVO;
import me.zhengjie.modules.meal.domain.dto.MealRefundQueryCriteria;
import me.zhengjie.utils.PageResult;

/**
 * 退餐服务接口
 * @author qqx
 * @date 2026-04-19
 */
public interface MealRefundService {

    /**
     * 执行退餐
     * @param dto 退餐请求参数
     * @return 退餐日志
     */
    MealRefundLog refund(MealRefundDto dto);

    /**
     * 查询退餐日志（分页）
     * @param criteria 查询条件
     * @return 退餐日志分页结果
     */
    PageResult<MealRefundLogVO> queryAll(MealRefundQueryCriteria criteria);

    /**
     * 根据订单ID查询退餐日志
     * @param orderId 订单ID
     * @return 退餐日志
     */
    MealRefundLog queryByOrderId(Long orderId);
}
