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
package me.zhengjie.modules.customer.pkg.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 父套餐实体
 *
 * @author Zheng Jie
 * @author qqx
 * @date 2026-04-13
 */
@Getter
@Setter
@TableName("parent_package")
public class ParentPackage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 套餐编码
     */
    private String packageCode;

    /**
     * 编号前缀（单个大写字母，如 A）
     */
    private String prefix;

    /**
     * 编号池前缀（如A1，与套餐prefix区分，池配置独立可调）
     */
    @TableField("pool_prefix")
    private String poolPrefix;

    /**
     * 编号池起始号（如1001）
     */
    @TableField("pool_start")
    private Integer poolStart;

    /**
     * 编号池结束号（如1199）
     */
    @TableField("pool_end")
    private Integer poolEnd;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 状态（1=启用，0=停用）
     */
    private Boolean status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updateTime;
}
