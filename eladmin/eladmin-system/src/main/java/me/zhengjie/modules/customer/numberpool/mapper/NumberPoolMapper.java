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
package me.zhengjie.modules.customer.numberpool.mapper;

import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NumberPoolMapper {

    /**
     * SELECT FOR UPDATE — 锁定 parent_package 行.
     * 锁加在套餐行而非 customer_profile，避免宽间隙锁.
     * 由调用方（NumberPoolServiceImpl）控制事务边界.
     */
    @Select("SELECT * FROM parent_package WHERE id = #{packageId} FOR UPDATE")
    ParentPackage selectForUpdate(@Param("packageId") Long packageId);

    /**
     * 查询 [pool_start, pool_end] 范围内已使用（且有进行中订单）的编号.
     * INNER JOIN customer_order WHERE status=1 — 只有进行中订单才占住编号.
     * 结果集按编号升序，用于在 Java 中扫描最小可用号.
     */
    List<String> findUsedCodesInRange(
        @Param("poolPrefix") String poolPrefix,
        @Param("rangeStartCode") String rangeStartCode,
        @Param("rangeEndCode") String rangeEndCode
    );
}
