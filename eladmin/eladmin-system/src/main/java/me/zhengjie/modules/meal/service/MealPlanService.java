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
package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerAddressVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerItemVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.domain.dto.MealPlanListDetailVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanQueryCriteria;
import me.zhengjie.utils.PageResult;

import java.util.List;

/**
 * 排餐计划服务
 * @author qqx
 * @date 2026-03-31
 **/
public interface MealPlanService {

    /**
     * 根据排餐日期和餐次生成排餐计划。
     *
     * @param recordDate 排餐日期，格式 yyyy-MM-dd
     * @param mealType 餐次，支持 BREAKFAST / LUNCH / DINNER
     * @param customerId 指定客户ID，不传则生成全部客户的排餐计划
     * @param menuWeekNum 指定菜单周次，需与 menuDayOfWeek 同时传入；不传则按 recordDate 推导
     * @param menuDayOfWeek 指定菜单星期，需与 menuWeekNum 同时传入；不传则按 recordDate 推导
     * @return 生成结果汇总
     */
    MealPlanGenerateResult generateMealPlan(String recordDate, String mealType, Long customerId, Integer menuWeekNum, Integer menuDayOfWeek);

    /**
     * 根据排餐日期和餐次生成排餐计划。
     *
     * @param recordDate 排餐日期，格式 yyyy-MM-dd
     * @param mealType 餐次，支持 BREAKFAST / LUNCH / DINNER
     * @param customerId 指定客户ID，不传则生成全部客户的排餐计划
     * @return 生成结果汇总
     */
    default MealPlanGenerateResult generateMealPlan(String recordDate, String mealType, Long customerId) {
        return generateMealPlan(recordDate, mealType, customerId, null, null);
    }

    /**
     * 分页查询排餐计划列表
     *
     * @param criteria 查询条件
     * @return 分页结果
     */
    PageResult<MealPlan> queryAll(MealPlanQueryCriteria criteria);

    /**
     * 分页查询排餐计划列表（包含客户与菜品详情）
     *
     * @param criteria 查询条件
     * @return 分页结果
     */
    PageResult<MealPlanListDetailVO> queryAllWithDetail(MealPlanQueryCriteria criteria);

    /**
     * 根据ID查询排餐计划
     *
     * @param id 排餐计划ID
     * @return 排餐计划
     */
    MealPlan queryById(Long id);

    /**
     * 分页查询排餐计划客户列表
     *
     * @param criteria 查询条件
     * @return 分页结果
     */
    PageResult<MealPlanCustomer> queryCustomers(MealPlanCustomerQueryCriteria criteria);

    /**
     * 根据ID查询排餐计划客户
     *
     * @param id 客户计划ID
     * @return 排餐计划客户
     */
    MealPlanCustomer queryCustomerById(Long id);

    /**
     * 查询客户排餐菜品明细
     *
     * @param customerPlanId 客户计划ID
     * @return 菜品明细列表
     */
    List<MealPlanCustomerItemVO> queryCustomerItems(Long customerPlanId);

    /**
     * 根据日期和餐次删除排餐计划（级联删除客户和明细）
     *
     * @param recordDate 排餐日期
     * @param mealType 餐次
     * @param customerId 指定客户ID，不传则删除全部
     */
    void deleteMealPlan(String recordDate, String mealType, Long customerId);

    /**
     * 根据ID列表批量删除排餐计划（级联删除客户和明细）
     *
     * @param ids 排餐计划ID列表
     */
    void deleteMealPlans(List<Long> ids);

    /**
     * 根据客户计划ID列表批量删除客户排餐计划（级联删除明细）
     *
     * @param customerPlanIds 客户计划ID列表
     */
    void deleteMealPlanCustomers(List<Long> customerPlanIds);

    /**
     * 删除客户指定日期餐次的未核销排餐记录；若存在已核销记录则抛出异常。
     *
     * @param customerId 客户ID
     * @param recordDate 排餐日期，格式 yyyy-MM-dd
     * @param mealType 餐次，支持 BREAKFAST / LUNCH / DINNER
     * @return 删除的客户排餐记录数量
     */
    int deleteUnverifiedCustomerMealForCalendarAdjustment(Long customerId, String recordDate, String mealType);

    /**
     * 根据排餐计划ID查询完整详情（含客户列表和菜品明细）
     *
     * @param mealPlanId 排餐计划ID
     * @return 完整详情
     */
    MealPlanDetailVO queryMealPlanDetail(Long mealPlanId);

    /**
     * 按日期统计各父套餐餐数
     *
     * @param date 排餐日期，格式 yyyy-MM-dd
     * @return 套餐统计列表
     */
    List<MealPackageStatDto> statByDate(String date);

    /**
     * 根据排餐计划ID查询客户配送地址信息
     *
     * @param mealPlanId 排餐计划ID
     * @return 客户配送地址列表
     */
    List<MealPlanCustomerAddressVO> queryCustomerAddresses(Long mealPlanId);
}
