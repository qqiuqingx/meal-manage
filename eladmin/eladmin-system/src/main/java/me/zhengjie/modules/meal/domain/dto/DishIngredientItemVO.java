/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may obtain a copy of the License at
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

/**
 * 菜品配料响应 DTO
 * @author qqx
 * @date 2026-04-02
 **/
@Data
public class DishIngredientItemVO {

    @ApiModelProperty(value = "配料ID")
    private Integer ingredientId;

    @ApiModelProperty(value = "配料名称")
    private String ingredientName;

    @ApiModelProperty(value = "单位")
    private String unit;

    @ApiModelProperty(value = "用量")
    private Double quantity;

    @ApiModelProperty(value = "备注")
    private String remark;
}
