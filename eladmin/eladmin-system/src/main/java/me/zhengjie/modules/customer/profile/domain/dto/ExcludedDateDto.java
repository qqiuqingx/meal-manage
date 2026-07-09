/*
 *  Copyright 2019-2025 Zheng Jie
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
package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 排除日期 DTO
 * @author qqx
 * @date 2026-04-14
 **/
@Data
public class ExcludedDateDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 排除日期，格式：yyyy-MM-dd
     */
    private String date;

    /**
     * 排除的餐次类型列表：BREAKFAST、LUNCH、DINNER
     */
    private java.util.List<String> mealTypes;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public java.util.List<String> getMealTypes() {
        return mealTypes;
    }

    public void setMealTypes(java.util.List<String> mealTypes) {
        this.mealTypes = mealTypes;
    }
}
