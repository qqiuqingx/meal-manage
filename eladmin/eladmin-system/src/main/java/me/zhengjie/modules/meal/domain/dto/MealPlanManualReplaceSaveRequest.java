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

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 手工换菜保存请求
 * @author qqx
 * @date 2026-05-28
 **/
@Data
public class MealPlanManualReplaceSaveRequest {

    @NotEmpty(message = "换菜项不能为空")
    @Valid
    @ApiModelProperty(value = "换菜项列表")
    private List<ReplaceItem> items;

    @Data
    public static class ReplaceItem {

        @NotNull(message = "菜品ID不能为空")
        @ApiModelProperty(value = "换菜菜品ID")
        private Integer dishId;

        @NotNull(message = "菜品类目不能为空")
        @ApiModelProperty(value = "菜品类目：MAIN/SIDE/VEGETABLE/SOUP/RICE")
        private String dishType;

        @NotEmpty(message = "客户排餐记录ID不能为空")
        @ApiModelProperty(value = "客户排餐记录ID列表")
        private List<Long> customerPlanIds;
    }
}
