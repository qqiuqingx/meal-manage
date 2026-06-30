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
package me.zhengjie.modules.customer.numberpool.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 编号池配置数据对象（从 ParentPackage 字段构造，传入 NumberPoolService.allocate）
 * @author qqx
 * @date 2026-04-13
 */
@Getter
@Setter
public class NumberPoolConfig {

    /** 编号池前缀（如 A1） */
    private String poolPrefix;

    /** 起始号（如 1001） */
    private Integer poolStart;

    /** 结束号（如 1199） */
    private Integer poolEnd;

    /** 套餐ID（用于定位 parent_package 行以加 FOR UPDATE 锁） */
    private Long packageId;
}
