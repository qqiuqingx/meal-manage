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
package me.zhengjie.modules.meal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 排餐计划客户结果 Mapper
 * @author qqx
 * @date 2026-03-31
 **/
@Mapper
public interface MealPlanCustomerMapper extends BaseMapper<MealPlanCustomer> {

    /**
     * 分页查询排餐计划客户列表
     */
    Page<MealPlanCustomer> selectPageByCriteria(@Param("criteria") MealPlanCustomerQueryCriteria criteria, Page<MealPlanCustomer> page);

    /**
     * 根据ID查询客户计划
     */
    MealPlanCustomer selectById(@Param("id") Long id);

    /**
     * 根据ID列表批量查询客户计划
     */
    List<MealPlanCustomer> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 根据客户计划ID列表批量软删除客户计划
     */
    int softDeleteByIds(@Param("ids") List<Long> ids);

    /**
     * 根据排餐计划ID查询所有客户
     */
    List<MealPlanCustomer> selectByMealPlanId(@Param("mealPlanId") Long mealPlanId);

    /**
     * 根据排餐计划ID列表批量查询所有客户
     */
    List<MealPlanCustomer> selectByMealPlanIds(@Param("mealPlanIds") List<Long> mealPlanIds);

    /**
     * 按日期统计各父套餐餐数
     */
    List<MealPackageStatDto> statByDate(@Param("date") String date);

    /**
     * 仅在未核销时更新核销状态，避免并发重复核销
     */
    int markVerifiedIfPending(@Param("id") Long id,
                              @Param("verificationTime") java.util.Date verificationTime,
                              @Param("verificationOperator") String verificationOperator);
}
