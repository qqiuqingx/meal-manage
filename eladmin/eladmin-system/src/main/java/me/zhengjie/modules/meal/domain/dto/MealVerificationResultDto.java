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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 核销结果DTO
 * @author qqx
 * @date 2026-04-05
 **/
@Getter
@Setter
public class MealVerificationResultDto implements Serializable {

    @ApiModelProperty(value = "成功数量")
    private Integer successCount = 0;

    @ApiModelProperty(value = "失败数量")
    private Integer failCount = 0;

    @ApiModelProperty(value = "失败原因列表")
    private List<String> failReasons = new ArrayList<>();
}
