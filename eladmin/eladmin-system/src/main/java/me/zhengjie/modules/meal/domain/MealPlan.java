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
package me.zhengjie.modules.meal.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import me.zhengjie.base.BaseEntity;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * 排餐计划主表
 * @author qqx
 * @date 2026-03-31
 **/
@Getter
@Setter
@TableName("meal_plan")
public class MealPlan extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "排餐日期")
    private LocalDate recordDate;

    @ApiModelProperty(value = "餐次")
    private String mealType;

    @ApiModelProperty(value = "总数")
    private Integer totalCount;

    @ApiModelProperty(value = "成功数")
    private Integer successCount;

    @ApiModelProperty(value = "失败数")
    private Integer failCount;

    @ApiModelProperty(value = "状态")
    private String status;

    @ApiModelProperty(value = "生成时间")
    private Timestamp generateTime;

    @ApiModelProperty(value = "是否删除")
    private Boolean deleted;
}
