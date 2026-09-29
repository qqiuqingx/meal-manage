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
import me.zhengjie.modules.customer.profile.domain.dto.CustomerScheduledMealDto;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerAddressVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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
     * 仅软删除仍未核销的客户排餐记录，避免日历调整与核销并发时误删已核销事实。
     *
     * @param ids 客户排餐记录ID
     * @return 实际更新行数
     */
    int softDeleteUnverifiedByIds(@Param("ids") List<Long> ids);

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

    /**
     * 根据排餐计划ID查询客户的配送地址信息
     */
    List<MealPlanCustomerAddressVO> selectCustomerAddresses(@Param("mealPlanId") Long mealPlanId);

    /**
     * 回退核销状态（删除核销日志时调用）
     * @param id 客户排餐ID
     * @return 更新行数
     */
    int revertVerified(@Param("id") Long id);

    /**
     * 批量查询各订单当前餐数池成功且有效的已排份数。
     * 早餐按 BREAKFAST 单独统计；午餐/晚餐按共享餐池合并统计 LUNCH + DINNER。
     *
     * @param orderIds 订单ID列表
     * @param mealType 当前生成的餐次（BREAKFAST/LUNCH/DINNER）
     * @return 订单ID -> 当前餐数池已排数量 的映射
     */
    List<me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto> countScheduledByOrderIds(@Param("orderIds") List<Long> orderIds, @Param("mealType") String mealType);

    /**
     * 批量查询各订单全部有效排餐数量。
     * 统计口径：meal_plan_customer.deleted=0 且 meal_plan.deleted=0。
     *
     * @param orderIds 订单ID列表
     * @return 订单ID -> 已排餐总数 的映射
     */
    List<me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto> countAllScheduledByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 批量统计各订单成功且未删除的排餐份数。
     *
     * @param orderIds 订单ID列表
     * @return 订单ID -> 成功排餐份数
     */
    List<me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto> countSuccessfulScheduledByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 批量查询各订单今天已排餐但未核销的数量。
     * @param orderIds 订单ID列表
     * @param recordDate 统计日期
     * @return 订单ID -> 今日未核销排餐数量 的映射
     */
    List<me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto> countTodayUnverifiedScheduledByOrderIds(@Param("orderIds") List<Long> orderIds,
                                                                                                             @Param("recordDate") LocalDate recordDate);

    /**
     * 按订单读取指定日期范围的成功、失败及核销排餐进度。
     *
     * @param orderId 订单ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 当前订单按日期和餐次聚合的进度
     */
    List<CustomerScheduledMealDto> selectScheduledMealsByOrderIdAndDateRange(@Param("orderId") Long orderId,
                                                                             @Param("startDate") LocalDate startDate,
                                                                             @Param("endDate") LocalDate endDate);

    /**
     * 查询当前订单、日期和餐次的生成结果，不包含同客户其他订单。
     */
    List<me.zhengjie.modules.meal.domain.dto.CustomerGeneratedMealPlanDto> selectGeneratedByOrderDateMeal(
            @Param("customerId") Long customerId,
            @Param("orderId") Long orderId,
            @Param("recordDate") LocalDate recordDate,
            @Param("mealType") String mealType);

    /**
     * 按餐次统计订单全部有效的成功排餐份数，供早餐和午晚餐池分别校验。
     */
    List<me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto> countSuccessfulScheduledByOrderIdGroupedByMealType(
            @Param("orderId") Long orderId);

    /**
     * 根据排餐计划ID查询所有未核销的客户排餐记录
     * @param mealPlanId 排餐计划ID
     * @return 未核销的客户排餐记录列表
     */
    List<MealPlanCustomer> selectUnverifiedByMealPlanId(@Param("mealPlanId") Long mealPlanId);

    /**
     * 批量查询当前页面中哪些客户排餐记录属于订单当前餐次的首次成功排餐
     * @param customerPlanIds 当前排餐计划中的客户计划ID列表
     * @return 首次成功排餐的客户计划ID列表
     */
    List<Long> selectFirstSuccessfulCustomerPlanIds(@Param("customerPlanIds") List<Long> customerPlanIds);

    /**
     * 批量查询各订单在指定日期各餐次的有效排餐数（状态为成功且未删除）。
     * @param orderIds 订单ID列表
     * @param recordDate 排餐日期
     * @return 订单ID + 餐次 -> 排餐数量 的映射
     */
    List<me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto> countSuccessfulScheduledByOrderIdsAndDate(@Param("orderIds") List<Long> orderIds,
                                                                                                               @Param("recordDate") LocalDate recordDate);
}
