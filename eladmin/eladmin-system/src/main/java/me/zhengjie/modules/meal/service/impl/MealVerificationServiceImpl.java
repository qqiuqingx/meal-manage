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
package me.zhengjie.modules.meal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.MealVerificationLog;
import me.zhengjie.modules.meal.domain.dto.MealVerificationDto;
import me.zhengjie.modules.meal.domain.dto.MealVerificationResultDto;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import me.zhengjie.modules.meal.service.MealVerificationService;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 核销服务实现
 * @author qqx
 * @date 2026-04-05
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class MealVerificationServiceImpl implements MealVerificationService {

    private final MealVerificationLogMapper verificationLogMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;
    private final MealPlanMapper mealPlanMapper;
    private final CustomerOrderMapper customerOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealVerificationResultDto verify(MealVerificationDto dto) {
        MealVerificationResultDto result = new MealVerificationResultDto();

        for (Long customerPlanId : dto.getCustomerPlanIds()) {
            try {
                verifySingle(customerPlanId, dto.getRemark());
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                log.error("核销失败，客户计划ID: {}, 错误: {}", customerPlanId, e.getMessage());
                result.setFailCount(result.getFailCount() + 1);
                result.getFailReasons().add("客户ID " + customerPlanId + ": " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * 单个核销
     */
    private void verifySingle(Long customerPlanId, String remark) {
        // 1. 获取客户排餐信息
        MealPlanCustomer customerPlan = mealPlanCustomerMapper.selectById(customerPlanId);
        if (customerPlan == null) {
            throw new BadRequestException("客户排餐记录不存在");
        }
        log.info("开始核销：{}",JSONUtil.toJsonStr(customerPlan));

        // 2. 检查是否已核销
        if (customerPlan.getIsVerified() != null && customerPlan.getIsVerified() == 1) {
            throw new BadRequestException("该客户已完成核销，请勿重复操作");
        }

        // 3. 获取排餐计划信息
        MealPlan mealPlan = mealPlanMapper.selectById(customerPlan.getMealPlanId());
        if (mealPlan == null) {
            throw new BadRequestException("排餐计划不存在");
        }

        // 4. 获取订单信息
        CustomerOrder order = customerOrderMapper.selectById(customerPlan.getOrderId());
        if (order == null) {
            throw new BadRequestException("关联订单不存在");
        }

        // 5. 检查订单剩余餐数
        if (order.getRemainingCount() == null || order.getRemainingCount() <= 0) {
            throw new BadRequestException("订单剩余餐数不足，当前剩余: " + (order.getRemainingCount() == null ? 0 : order.getRemainingCount()));
        }

        // 6. 计算核销单价
        BigDecimal price = null;
        String mealType = mealPlan.getMealType();
        if ("LUNCH".equals(mealType) || "DINNER".equals(mealType)) {
            // 午餐和晚餐使用 lunchDinnerPrice
            price = order.getLunchDinnerPrice();
        } else if ("BREAKFAST".equals(mealType)) {
            // 早餐使用 breakfastPrice
            price = order.getBreakfastPrice();
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("订单单价配置异常，无法核销");
        }

        // 7. 原子更新客户排餐核销状态，避免并发重复核销
        Date verificationTime = new Date();
        String operator = SecurityUtils.getCurrentUsername();
        int planUpdated = mealPlanCustomerMapper.markVerifiedIfPending(customerPlanId, verificationTime, operator);
        if (planUpdated == 0) {
            throw new BadRequestException("该客户已完成核销，请勿重复操作");
        }

        // 8. 更新订单：核销餐数+1，剩余餐数-1，核销金额+单价，餐费余额-单价
        int updated = customerOrderMapper.incrementVerifiedCountAndAmount(order.getId(), price);
        if (updated == 0) {
            throw new BadRequestException("订单更新失败，可能是并发操作导致");
        }

        // 9. 重新读取订单，基于最新状态生成准确日志
        CustomerOrder latestOrder = customerOrderMapper.selectById(customerPlan.getOrderId());
        if (latestOrder == null) {
            throw new BadRequestException("关联订单不存在");
        }

        // 10. 记录核销日志
        MealVerificationLog log = new MealVerificationLog();
        log.setMealPlanCustomerId(customerPlanId);
        log.setMealPlanId(customerPlan.getMealPlanId());
        log.setCustomerId(customerPlan.getCustomerId());
        log.setOrderId(customerPlan.getOrderId());
        // LocalDate 转 Date
        if (mealPlan.getRecordDate() != null) {
            log.setRecordDate(java.sql.Date.valueOf(mealPlan.getRecordDate()));
        }
        log.setMealType(mealPlan.getMealType());
        log.setVerificationCount(1);
        int remainingAfter = latestOrder.getRemainingCount() == null ? 0 : latestOrder.getRemainingCount();
        int verifiedTotalAfter = latestOrder.getVerifiedCount() == null ? 0 : latestOrder.getVerifiedCount();
        log.setRemainingBefore(remainingAfter + 1);
        log.setRemainingAfter(remainingAfter);
        log.setVerifiedTotalBefore(Math.max(verifiedTotalAfter - 1, 0));
        log.setVerifiedTotalAfter(verifiedTotalAfter);
        log.setOperator(operator);
        log.setOperateTime(verificationTime);
        log.setRemark(remark);
        verificationLogMapper.insert(log);
    }

    @Override
    public MealVerificationLog queryById(Long id) {
        return verificationLogMapper.selectById(id);
    }

    @Override
    public List<MealVerificationLog> queryByOrderId(Long orderId) {
        QueryWrapper<MealVerificationLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", orderId).orderByDesc("operate_time", "id");
        return verificationLogMapper.selectList(queryWrapper);
    }
}
