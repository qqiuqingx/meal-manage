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
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.List;

/**
 * 核销请求DTO
 * @author qqx
 * @date 2026-04-05
 **/
@Getter
@Setter
public class MealVerificationDto implements Serializable {

    @NotEmpty(message = "客户排餐ID列表不能为空")
    @ApiModelProperty(value = "客户排餐ID列表", required = true)
    private List<Long> customerPlanIds;

    @ApiModelProperty(value = "备注")
    private String remark;
}
