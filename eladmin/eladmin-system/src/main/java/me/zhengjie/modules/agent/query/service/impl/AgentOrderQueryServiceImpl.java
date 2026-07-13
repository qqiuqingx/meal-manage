package me.zhengjie.modules.agent.query.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderMealBalanceDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderSummaryDto;
import me.zhengjie.modules.agent.query.service.AgentOrderQueryService;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealBalanceDto;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealVerifiedCountDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.service.OrderMealBalanceCalculator;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.meal.domain.dto.OrderAssociatedRecordCountDto;
import me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealRefundLogMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 订单只读查询实现。查询实体后立即转换为无金额的专用 DTO，禁止透传订单实体。
 */
@Service
@RequiredArgsConstructor
public class AgentOrderQueryServiceImpl implements AgentOrderQueryService {

    private static final int MAX_PAGE_SIZE = 20;

    private final CustomerOrderMapper customerOrderMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final SubPackageMapper subPackageMapper;
    private final MealVerificationLogMapper mealVerificationLogMapper;
    private final MealRefundLogMapper mealRefundLogMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;

    /** {@inheritDoc} */
    @Override
    public AgentListResultDto<AgentOrderSummaryDto> listByCustomer(Long customerId, Integer status, int page, int size) {
        AgentListResultDto<AgentOrderSummaryDto> result = new AgentListResultDto<>();
        if (customerId == null || customerId <= 0 || !AgentCustomerDataScopeContext.allows(customerId)) return result;
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<CustomerOrder> wrapper = new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getCustomerId, customerId)
                .orderByDesc(CustomerOrder::getCreateTime);
        if (status != null) wrapper.eq(CustomerOrder::getStatus, status);
        List<CustomerOrder> allOrders = customerOrderMapper.selectList(wrapper);
        result.setTotal(allOrders.size());
        int fromIndex = Math.min((safePage - 1) * safeSize, allOrders.size());
        int toIndex = Math.min(fromIndex + safeSize, allOrders.size());
        result.setTruncated(toIndex < allOrders.size());
        result.setItems(toSummaries(allOrders.subList(fromIndex, toIndex)));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentListResultDto<AgentOrderSummaryDto> listForOverview(Long customerId) {
        AgentListResultDto<AgentOrderSummaryDto> result = new AgentListResultDto<>();
        if (customerId == null || customerId <= 0 || !AgentCustomerDataScopeContext.allows(customerId)) return result;
        List<CustomerOrder> orders = customerOrderMapper.selectList(new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getCustomerId, customerId).orderByDesc(CustomerOrder::getCreateTime));
        result.setTotal(orders.size());
        int limit = Math.min(orders.size(), 200);
        result.setTruncated(limit < orders.size());
        result.setItems(toSummaries(orders.subList(0, limit)));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentOrderSummaryDto getDetail(Long orderId, String orderCode, Long expectedCustomerId) {
        CustomerOrder order = null;
        if (orderId != null && orderId > 0) {
            order = customerOrderMapper.selectById(orderId);
        } else if (orderCode != null && !orderCode.trim().isEmpty()) {
            order = customerOrderMapper.selectOne(new LambdaQueryWrapper<CustomerOrder>()
                    .eq(CustomerOrder::getOrderCode, orderCode.trim()).last("LIMIT 1"));
        }
        if (order == null || !AgentCustomerDataScopeContext.allows(order.getCustomerId())
            || (expectedCustomerId != null && !expectedCustomerId.equals(order.getCustomerId()))) return null;
        List<AgentOrderSummaryDto> summaries = toSummaries(Collections.singletonList(order));
        return summaries.isEmpty() ? null : summaries.get(0);
    }

    /**
     * 将订单实体转换为 Agent 数据契约，并批量查询未删除核销汇总避免 N+1 查询。
     *
     * @param orders 已按权限过滤的订单实体
     * @return 不含金额字段的订单摘要
     */
    private List<AgentOrderSummaryDto> toSummaries(List<CustomerOrder> orders) {
        if (orders == null || orders.isEmpty()) return new ArrayList<>();
        Map<Long, OrderMealBalanceDto> balanceByOrder = loadBalances(orders);
        Map<Long, String> parentNames = parentNames(orders);
        Map<Long, String> childNames = childNames(orders);
        Map<Long, Integer> verificationCounts = relationCounts(mealVerificationLogMapper.countActiveByOrderIds(orderIds(orders)));
        Map<Long, Integer> refundCounts = relationCounts(mealRefundLogMapper.countByOrderIds(orderIds(orders)));
        Map<Long, Integer> mealPlanCounts = scheduledCounts(mealPlanCustomerMapper.countAllScheduledByOrderIds(orderIds(orders)));
        return orders.stream().map(order -> toSummary(order, balanceByOrder.get(order.getId()), parentNames, childNames,
                        verificationCounts, refundCounts, mealPlanCounts))
                .collect(Collectors.toList());
    }

    /**
     * 提取可用于批量聚合查询的订单 ID，避免为单笔订单发起关联查询。
     *
     * @param orders 已按权限过滤的订单列表
     * @return 去重后的有效订单 ID
     */
    private List<Long> orderIds(List<CustomerOrder> orders) {
        return orders.stream().map(CustomerOrder::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private Map<Long, OrderMealBalanceDto> loadBalances(List<CustomerOrder> orders) {
        List<Long> ids = orderIds(orders);
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Long, int[]> verified = new HashMap<>();
        List<OrderMealVerifiedCountDto> rows = customerOrderMapper.sumVerifiedCountByOrderIds(ids);
        if (rows != null) {
            for (OrderMealVerifiedCountDto row : rows) {
                if (row == null || row.getOrderId() == null) continue;
                int[] counts = verified.computeIfAbsent(row.getOrderId(), ignored -> new int[3]);
                int count = row.getVerifiedCount() == null ? 0 : row.getVerifiedCount();
                if ("BREAKFAST".equals(row.getMealType())) counts[0] += count;
                if ("LUNCH".equals(row.getMealType())) counts[1] += count;
                if ("DINNER".equals(row.getMealType())) counts[2] += count;
            }
        }
        Map<Long, OrderMealBalanceDto> result = new HashMap<>();
        for (CustomerOrder order : orders) {
            int[] counts = verified.getOrDefault(order.getId(), new int[3]);
            result.put(order.getId(), OrderMealBalanceCalculator.calculate(order, counts[0], counts[1], counts[2]));
        }
        return result;
    }

    private Map<Long, String> parentNames(List<CustomerOrder> orders) {
        Set<Long> ids = orders.stream().map(CustomerOrder::getParentPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return parentPackageMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ParentPackage::getId, ParentPackage::getPackageName));
    }

    private Map<Long, String> childNames(List<CustomerOrder> orders) {
        Set<Long> ids = orders.stream().map(CustomerOrder::getChildPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return subPackageMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SubPackage::getId, SubPackage::getSubPackageName));
    }

    /**
     * 将核销或退餐聚合行转换为订单维度的记录数映射。
     *
     * @param rows 关联记录统计行
     * @return 订单 ID 到非负记录数的映射
     */
    private Map<Long, Integer> relationCounts(List<OrderAssociatedRecordCountDto> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyMap();
        return rows.stream().filter(Objects::nonNull).filter(item -> item.getOrderId() != null)
                .collect(Collectors.toMap(OrderAssociatedRecordCountDto::getOrderId,
                        item -> Math.max(item.getRecordCount() == null ? 0 : item.getRecordCount(), 0), Integer::sum));
    }

    /**
     * 将有效排餐聚合行转换为订单维度的记录数映射。
     *
     * @param rows 排餐记录统计行
     * @return 订单 ID 到非负排餐记录数的映射
     */
    private Map<Long, Integer> scheduledCounts(List<OrderScheduledCountDto> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyMap();
        return rows.stream().filter(Objects::nonNull).filter(item -> item.getOrderId() != null)
                .collect(Collectors.toMap(OrderScheduledCountDto::getOrderId,
                        item -> Math.max(item.getScheduledCount() == null ? 0 : item.getScheduledCount(), 0), Integer::sum));
    }

    private AgentOrderSummaryDto toSummary(CustomerOrder order, OrderMealBalanceDto balance,
                                           Map<Long, String> parentNames, Map<Long, String> childNames,
                                           Map<Long, Integer> verificationCounts, Map<Long, Integer> refundCounts,
                                           Map<Long, Integer> mealPlanCounts) {
        AgentOrderSummaryDto dto = new AgentOrderSummaryDto();
        dto.setOrderId(order.getId());
        dto.setOrderCode(order.getOrderCode());
        dto.setCustomerId(order.getCustomerId());
        dto.setCustomerCode(order.getCustomerCode());
        dto.setStatusCode(order.getStatus());
        dto.setStatusName(statusName(order.getStatus()));
        dto.setStartDate(order.getStartDate());
        dto.setStartMealTypeCode(order.getStartMealType());
        dto.setEndDate(order.getEndDate());
        dto.setMealTypeCode(order.getMealType());
        dto.setScheduleModeCode(order.getScheduleMode());
        dto.setParentPackageId(order.getParentPackageId());
        dto.setParentPackageName(parentNames.get(order.getParentPackageId()));
        dto.setChildPackageId(order.getChildPackageId());
        dto.setChildPackageName(childNames.get(order.getChildPackageId()));
        dto.setVerificationRecordCount(verificationCounts.getOrDefault(order.getId(), 0));
        dto.setRefundRecordCount(refundCounts.getOrDefault(order.getId(), 0));
        dto.setMealPlanRecordCount(mealPlanCounts.getOrDefault(order.getId(), 0));
        AgentOrderMealBalanceDto mealBalance = toBalance(balance);
        mealBalance.setBreakfastCount(order.getBreakfastCount() == null ? 0 : order.getBreakfastCount());
        mealBalance.setLunchDinnerCount(order.getLunchDinnerCount() == null ? 0 : order.getLunchDinnerCount());
        dto.setMealBalance(mealBalance);
        return dto;
    }

    private AgentOrderMealBalanceDto toBalance(OrderMealBalanceDto source) {
        AgentOrderMealBalanceDto dto = new AgentOrderMealBalanceDto();
        if (source == null) return dto;
        dto.setVerifiedBreakfast(source.getVerifiedBreakfast());
        dto.setVerifiedLunch(source.getVerifiedLunch());
        dto.setVerifiedDinner(source.getVerifiedDinner());
        dto.setRemainingBreakfast(source.getRemainingBreakfast());
        dto.setRemainingLunchDinner(source.getRemainingLunchDinner());
        return dto;
    }

    private String statusName(Integer status) {
        if (Integer.valueOf(0).equals(status)) return "已取消";
        if (Integer.valueOf(1).equals(status)) return "进行中";
        if (Integer.valueOf(2).equals(status)) return "已完成";
        if (Integer.valueOf(3).equals(status)) return "已退餐";
        return "未知";
    }
}
