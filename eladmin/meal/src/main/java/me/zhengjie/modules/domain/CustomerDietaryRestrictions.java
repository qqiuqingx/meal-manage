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
package me.zhengjie.modules.domain;

import lombok.Data;
import cn.hutool.core.bean.BeanUtil;
import io.swagger.annotations.ApiModelProperty;
import cn.hutool.core.bean.copier.CopyOptions;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;

/**
* @description /
* @author qqx
* @date 2026-03-14
**/
@Data
@TableName("customer_dietary_restrictions")
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
    private String restrictions;

    @ApiModelProperty(value = "orderDate")
    @TableField("order_date")
    private String orderDate;

    @ApiModelProperty(value = "updateDate")
    @TableField("update_date")
    private String updateDate;

    @ApiModelProperty(value = "createdBy")
    private String createdBy;

    @ApiModelProperty(value = "updatedBy")
    private String updatedBy;

    @ApiModelProperty(value = "createTime")
    @TableField("created_at")
    private String createTime;

    @ApiModelProperty(value = "updateTime")
    @TableField("updated_at")
    private String updateTime;

    @ApiModelProperty(value = "餐数")
    private Integer num;

    @ApiModelProperty(value = "开始时间")
    @TableField("start_date")
    private String startDate;

    @ApiModelProperty(value = "结束时间")
    @TableField("end_date")
    private String endDate;

    public void copy(CustomerDietaryRestrictions source){
        BeanUtil.copyProperties(source,this, CopyOptions.create().setIgnoreNullValue(true));
    }
}
