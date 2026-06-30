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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.meal.domain.MealRefundLog;
import me.zhengjie.modules.meal.domain.MealVerificationLog;
import me.zhengjie.modules.meal.domain.dto.MealRefundDto;
import me.zhengjie.modules.meal.domain.dto.MealRefundLogVO;
import me.zhengjie.modules.meal.domain.dto.MealRefundQueryCriteria;
import me.zhengjie.modules.meal.mapper.MealRefundLogMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import me.zhengjie.modules.meal.service.MealRefundService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 退餐服务实现
 * @author qqx
 * @date 2026-04-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MealRefundServiceImpl implements MealRefundService {

    private final CustomerOrderMapper customerOrderMapper;
    private final MealRefundLogMapper refundLogMapper;
    private final MealVerificationLogMapper verificationLogMapper;

    @Override
    public PageResult<MealRefundLogVO> queryAll(MealRefundQueryCriteria criteria) {
        Page<MealRefundLogVO> page = new Page<>(criteria.getPage() + 1, criteria.getSize());
        refundLogMapper.selectPageByCriteria(criteria, page);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealRefundLog refund(MealRefundDto dto) {
        // 1. 获取订单信息
        CustomerOrder order = customerOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }

        // 2. 校验订单状态（只有进行中的订单可以退餐）
        if (order.getStatus() != 1) {
            throw new BadRequestException("只有进行中的订单可以退餐，当前状态：" + getStatusDesc(order.getStatus()));
        }

        // 3. 查询该订单的核销日志，统计已核销的早餐和午晚餐数量
        Map<String, Integer> verifiedCounts = getVerifiedCountsByOrderId(dto.getOrderId());
        int verifiedBreakfastCount = verifiedCounts.getOrDefault("BREAKFAST", 0);
        int verifiedLunchDinnerCount = verifiedCounts.getOrDefault("LUNCH", 0) + verifiedCounts.getOrDefault("DINNER", 0);

        // 4. 计算剩余餐数
        int breakfastCount = order.getBreakfastCount() != null ? order.getBreakfastCount() : 0;
        int lunchDinnerCount = order.getLunchDinnerCount() != null ? order.getLunchDinnerCount() : 0;
        int remainingBreakfast = Math.max(breakfastCount - verifiedBreakfastCount, 0);
        int remainingLunchDinner = Math.max(lunchDinnerCount - verifiedLunchDinnerCount, 0);

        // 5. 计算退款金额 = 剩余早餐数 × 早餐单价 + 剩余午晚餐数 × 午晚餐单价
        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal breakfastPrice = order.getBreakfastPrice();
        BigDecimal lunchDinnerPrice = order.getLunchDinnerPrice();

        if (remainingBreakfast > 0 && breakfastPrice != null && breakfastPrice.compareTo(BigDecimal.ZERO) > 0) {
            refundAmount = refundAmount.add(breakfastPrice.multiply(BigDecimal.valueOf(remainingBreakfast)));
        }
        if (remainingLunchDinner > 0 && lunchDinnerPrice != null && lunchDinnerPrice.compareTo(BigDecimal.ZERO) > 0) {
            refundAmount = refundAmount.add(lunchDinnerPrice.multiply(BigDecimal.valueOf(remainingLunchDinner)));
        }

        // 6. 获取操作人
        String operator = SecurityUtils.getCurrentUsername();

        // 7. 更新订单状态为已退餐
        customerOrderMapper.updateStatusToRefunded(order.getId());

        // 8. 标记核销日志为已退餐
        verificationLogMapper.markAsRefunded(order.getId());

        // 9. 记录退餐日志
        MealRefundLog refundLog = new MealRefundLog();
        refundLog.setOrderId(order.getId());
        refundLog.setCustomerId(order.getCustomerId());
        refundLog.setRefundAmount(refundAmount);
        refundLog.setRefundBreakfastCount(remainingBreakfast);
        refundLog.setRefundLunchDinnerCount(remainingLunchDinner);
        refundLog.setVerifiedBreakfastCount(verifiedBreakfastCount);
        refundLog.setVerifiedLunchDinnerCount(verifiedLunchDinnerCount);
        refundLog.setRefundReason(dto.getRefundReason());
        refundLog.setOperator(operator);
        refundLog.setOperateTime(new Date());
        refundLogMapper.insert(refundLog);

        log.info("退餐成功，订单ID: {}，退款金额: {}，退早餐数: {}，退午晚餐数: {}，操作人: {}",
                order.getId(), refundAmount, remainingBreakfast, remainingLunchDinner, operator);

        return refundLog;
    }

    @Override
    public MealRefundLog queryByOrderId(Long orderId) {
        QueryWrapper<MealRefundLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", orderId);
        return refundLogMapper.selectOne(queryWrapper);
    }

    /**
     * 根据订单ID统计已核销的各餐次数量
     */
    private Map<String, Integer> getVerifiedCountsByOrderId(Long orderId) {
        Map<String, Integer> result = new HashMap<>();

        QueryWrapper<MealVerificationLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", orderId)
                .eq("is_refunded", 0)
                .and(wrapper -> wrapper.eq("deleted", 0).or().isNull("deleted"));

        List<MealVerificationLog> logs = verificationLogMapper.selectList(queryWrapper);

        for (MealVerificationLog log : logs) {
            String mealType = log.getMealType();
            Integer count = log.getVerificationCount() != null ? log.getVerificationCount() : 1;
            result.merge(mealType, count, Integer::sum);
        }

        return result;
    }

    /**
     * 获取订单状态描述
     */
    private String getStatusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0:
                return "已取消";
            case 1:
                return "进行中";
            case 2:
                return "已完成";
            case 3:
                return "已退餐";
            default:
                return "未知";
        }
    }
}
