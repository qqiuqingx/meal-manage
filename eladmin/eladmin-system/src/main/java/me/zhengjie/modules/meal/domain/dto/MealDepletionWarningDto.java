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
 * 餐数耗尽预警 DTO
 * @author qqx
 * @date 2026-05-28
 **/
@Data
public class MealDepletionWarningDto {
    /**
     * 订单ID
     */
    private Long orderId;
    /**
     * 客户ID
     */
    private Long customerId;
    /**
     * 客户编号
     */
    private String customerCode;
    /**
     * 客户姓名
     */
    private String customerName;
    /**
     * 预警餐数池：BREAKFAST=早餐，LUNCH_DINNER=午晚餐
     */
    private String mealType;
    /**
     * 预警餐数池名称
     */
    private String mealTypeName;
    /**
     * 当前餐数池剩余餐数
     */
    private Integer remainingCount;
    /**
     * 目标日期该餐数池排餐总数
     */
    private Integer tomorrowScheduledCount;
}
