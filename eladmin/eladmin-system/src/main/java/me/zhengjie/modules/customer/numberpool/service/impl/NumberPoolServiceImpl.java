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
package me.zhengjie.modules.customer.numberpool.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.numberpool.domain.NumberPoolConfig;
import me.zhengjie.modules.customer.numberpool.mapper.NumberPoolMapper;
import me.zhengjie.modules.customer.numberpool.service.NumberPoolService;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 编号池分配服务实现
 *
 * 并发安全策略（per PITFALLS.md Pitfall 1, 5）：
 * - 锁粒度在 parent_package 行，不在 customer_profile（避免宽间隙锁）
 * - 使用 @Transactional(isolation = Isolation.READ_COMMITTED)
 * - 整个 allocate() 方法是单一事务，锁在中途不释放
 *
 * @author qqx
 * @date 2026-04-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NumberPoolServiceImpl implements NumberPoolService {

    private final NumberPoolMapper numberPoolMapper;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public String allocate(NumberPoolConfig config) {
        // Step 1: SELECT FOR UPDATE — 锁定套餐行，序列化同套餐的所有并发分配请求
        // 不锁定 customer_profile，避免间隙锁影响其他并发插入
        ParentPackage parent = numberPoolMapper.selectForUpdate(config.getPackageId());
        if (parent == null) {
            throw new BadRequestException("父套餐不存在");
        }

        // Step 2: 查询 [pool_start, pool_end] 范围内已被占用的编号
        // 只有 customer_order.status = 1（进行中）的订单才算"占用"
        // 已取消/已完成订单的编号视为可用，可被重新分配（per POOL-11）
        List<String> usedCodes = numberPoolMapper.findUsedCodesInRange(
            config.getPoolPrefix(),
            config.getPoolStart(),
            config.getPoolEnd()
        );

        // Step 3: HashSet 加速 contains 判断
        Set<String> usedSet = new HashSet<>(usedCodes);

        // Step 4: 扫描 [pool_start, pool_end]，取最小的未占用编号
        // 升序扫描确保优先复用已释放的小编号（per POOL-05）
        int poolStart = config.getPoolStart();
        int poolEnd = config.getPoolEnd();
        for (int i = poolStart; i <= poolEnd; i++) {
            String candidate = config.getPoolPrefix() + String.format("%03d", i);
            if (!usedSet.contains(candidate)) {
                log.debug("编号池分配成功: packageId={}, code={}", config.getPackageId(), candidate);
                return candidate;
            }
        }

        // Step 5: 池满 — 超出 pool_end，无可用编号
        // 错误消息格式（per POOL-08）："套餐A1编号池已满(199/199)，请联系管理员扩容"
        int total = poolEnd - poolStart + 1;
        int used = usedCodes.size();
        String message = String.format(
            "套餐%s编号池已满(%d/%d)，请联系管理员扩容",
            config.getPoolPrefix(),
            used,
            total
        );
        throw new BadRequestException(message);
    }
}
