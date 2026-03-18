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

import lombok.Data;
import cn.hutool.core.bean.BeanUtil;
import io.swagger.annotations.ApiModelProperty;
import cn.hutool.core.bean.copier.CopyOptions;
import me.zhengjie.modules.meal.domain.enums.MealPackageEnum;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.io.Serializable;
import java.sql.Timestamp;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

/**
* @description /
* @author qqx
* @date 2026-03-14
**/
@Data
@TableName(value = "customer_dietary_restrictions", autoResultMap = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerDietaryRestrictions implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Integer id;

    @NotBlank
    @ApiModelProperty(value = "客户名称")
    @TableField("customer_name")
    private String customerName;

    @ApiModelProperty(value = "特殊要求")
    @TableField("special_needs")
    private String specialNeeds;

    @ApiModelProperty(value = "忌口")
    @TableField(value = "restrictions", typeHandler = JacksonTypeHandler.class)
    private List<String> restrictions;

    @ApiModelProperty(value = "updateDate")
    @TableField("update_date")
    private String updateDate;

    @ApiModelProperty(value = "createAt")
    @TableField("created_at")
    private String createAt;

    @ApiModelProperty(value = "updateAt")
    @TableField("updated_at")
    private String updateAt;

    @ApiModelProperty(value = "createTime")
    @TableField("create_time")
    private Timestamp createTime;

    @ApiModelProperty(value = "餐数")
    @TableField("num")
    private Integer num;

    @ApiModelProperty(value = "开始时间")
    @TableField("start_date")
    private String startDate;

    @ApiModelProperty(value = "结束时间")
    @TableField("end_date")
    private String endDate;

    @ApiModelProperty(value = "客户地址")
    @TableField("customer_address")
    private String customerAddress;

    @ApiModelProperty(value = "客户手机号")
    @TableField("phone")
    private String phone;

    @ApiModelProperty(value = "剩余餐数")
    @TableField("remaining_meals")
    private Integer remainingMeals;

    @NotNull(message = "套餐不能为空")
    @ApiModelProperty(value = "客户套餐")
    @TableField("meal_package")
    private String mealPackage;

    public void copy(CustomerDietaryRestrictions source){
        BeanUtil.copyProperties(source,this, CopyOptions.create().setIgnoreNullValue(true));
    }
}
