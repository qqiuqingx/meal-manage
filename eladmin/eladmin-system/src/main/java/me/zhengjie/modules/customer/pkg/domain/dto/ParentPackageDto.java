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
package me.zhengjie.modules.customer.pkg.domain.dto;

import lombok.Data;
import java.util.List;

/**
 * 父套餐 DTO（API 响应）
 *
 * @author qqx
 * @date 2026-04-13
 */
@Data
public class ParentPackageDto {

    private Long id;
    private String packageCode;
    private String prefix;

    /**
     * 编号池前缀（如A1）
     */
    private String poolPrefix;

    /**
     * 编号池起始号（如1001）
     */
    private Integer poolStart;

    /**
     * 编号池结束号（如1199）
     */
    private Integer poolEnd;

    private String packageName;
    private Integer status;
    private String remark;

    /**
     * 关联的子套餐列表（用于展开展示）
     */
    private List<SubPackageDto> children;
}
