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
package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;

/**
 * 订单已排餐数量统计
 * @author qqx
 * @date 2026-04-20
 **/
@Data
public class OrderScheduledCountDto {
    /**
     * 订单ID
     */
    private Long orderId;
    /**
     * 餐次类型
     */
    private String mealType;
    /**
     * 已排餐数量（未核销的排餐记录数）
     */
    private Integer scheduledCount;
}
