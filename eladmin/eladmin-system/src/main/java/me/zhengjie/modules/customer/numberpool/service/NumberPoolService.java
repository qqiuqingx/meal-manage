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
package me.zhengjie.modules.customer.numberpool.service;

import me.zhengjie.modules.customer.numberpool.domain.NumberPoolConfig;

/**
 * 编号池分配服务接口
 * @author qqx
 * @date 2026-04-13
 */
public interface NumberPoolService {

    /**
     * 从编号池分配最小可用编号（并发安全）.
     *
     * 全程在 @Transactional(isolation=Isolation.READ_COMMITTED) 内完成：
     * 1. SELECT FOR UPDATE 锁定 parent_package 行
     * 2. 扫描 [pool_start, pool_end]，跳过有进行中订单的编号
     * 3. 返回第一个可用编号
     *
     * @param config 编号池配置（poolPrefix, poolStart, poolEnd, packageId）
     * @return 分配的客户编号，如 "A11001"
     * @throws me.zhengjie.exception.BadRequestException 池满时抛出
     */
    String allocate(NumberPoolConfig config);
}
