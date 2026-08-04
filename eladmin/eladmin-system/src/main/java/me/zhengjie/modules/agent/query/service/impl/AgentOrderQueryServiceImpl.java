package me.zhengjie.modules.agent.query.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
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
        if (AgentCustomerDataScopeContext.status() == AgentCustomerDataScopeContext.ScopeStatus.UNBOUND
            || customerId != null && (customerId <= 0 || !AgentCustomerDataScopeContext.allows(customerId))) return result;
        Set<Long> scopedCustomerIds = AgentCustomerDataScopeContext.customerIds();
        if (customerId == null && scopedCustomerIds != null && scopedCustomerIds.isEmpty()) return result;
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<CustomerOrder> wrapper = new LambdaQueryWrapper<CustomerOrder>()
                .eq(customerId != null, CustomerOrder::getCustomerId, customerId)
                .in(customerId == null && scopedCustomerIds != null, CustomerOrder::getCustomerId, scopedCustomerIds)
                .orderByDesc(CustomerOrder::getCreateTime);
        if (status != null) wrapper.eq(CustomerOrder::getStatus, status);
        Page<CustomerOrder> orderPage = customerOrderMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<CustomerOrder> orders = orderPage == null || orderPage.getRecords() == null
            ? Collections.emptyList() : orderPage.getRecords();
        long total = orderPage == null ? 0L : orderPage.getTotal();
        result.setTotal(total);
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setTruncated((long) safePage * safeSize < total);
        result.setItems(toSummaries(orders));
        result.setQueriedAt(java.time.ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentListResultDto<AgentOrderSummaryDto> searchServiceCustomers(Long customerId, String customerCode,
                                                                            Long orderId, String orderCode,
                                                                            String status, String dealTimeFrom,
                                                                            String dealTimeTo, String packageCode,
                                                                            int page, int size) {
        AgentListResultDto<AgentOrderSummaryDto> result = new AgentListResultDto<>();
        if (AgentCustomerDataScopeContext.status() == AgentCustomerDataScopeContext.ScopeStatus.UNBOUND) return result;
        Set<Long> scopedCustomerIds = AgentCustomerDataScopeContext.customerIds();
        if (scopedCustomerIds != null && scopedCustomerIds.isEmpty()) return result;
        if (customerId != null && !AgentCustomerDataScopeContext.allows(customerId)) return result;
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<CustomerOrder> wrapper = new LambdaQueryWrapper<CustomerOrder>()
            .eq(customerId != null, CustomerOrder::getCustomerId, customerId)
            .eq(hasText(customerCode), CustomerOrder::getCustomerCode, trim(customerCode))
            .eq(orderId != null, CustomerOrder::getId, orderId)
            .eq(hasText(orderCode), CustomerOrder::getOrderCode, trim(orderCode))
            .in(scopedCustomerIds != null, CustomerOrder::getCustomerId, scopedCustomerIds)
            .orderByDesc(CustomerOrder::getDealTime)
            .orderByDesc(CustomerOrder::getCreateTime)
            .orderByDesc(CustomerOrder::getId);
        Integer statusCode = statusCode(status);
        if (statusCode != null) wrapper.eq(CustomerOrder::getStatus, statusCode);
        LocalDateTime from = parseDateBoundary(dealTimeFrom, false);
        LocalDateTime to = parseDateBoundary(dealTimeTo, true);
        wrapper.ge(from != null, CustomerOrder::getDealTime, from)
            .lt(to != null, CustomerOrder::getDealTime, to);
        applyPackageFilter(wrapper, packageCode);
        Page<CustomerOrder> orderPage = customerOrderMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<CustomerOrder> orders = orderPage == null || orderPage.getRecords() == null
            ? Collections.emptyList() : orderPage.getRecords();
        result.setTotal(orderPage == null ? 0L : orderPage.getTotal());
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setTruncated((long) safePage * safeSize < result.getTotal());
        result.setItems(toSummaries(orders));
        result.setQueriedAt(java.time.ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString());
        return result;
    }

    /** 将服务客户状态枚举转换为订单表中的固定状态代码。 */
    private Integer statusCode(String status) {
        if (!hasText(status) || "ALL".equalsIgnoreCase(status)) return null;
        if ("ACTIVE".equalsIgnoreCase(status)) return 1;
        if ("CANCELLED".equalsIgnoreCase(status)) return 0;
        if ("COMPLETED".equalsIgnoreCase(status)) return 2;
        if ("REFUNDED".equalsIgnoreCase(status)) return 3;
        throw new IllegalArgumentException("服务客户状态不在白名单内");
    }

    /** 将 yyyy-MM-dd 转为成交时间边界，结束日期使用次日零点作为开区间。 */
    private LocalDateTime parseDateBoundary(String value, boolean endExclusive) {
        if (!hasText(value)) return null;
        try {
            LocalDate date = LocalDate.parse(value.trim());
            return (endExclusive ? date.plusDays(1) : date).atStartOfDay();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("成交日期必须使用 yyyy-MM-dd 格式", exception);
        }
    }

    /** 将套餐编码固定解析为父套餐或子套餐 ID 集合，不允许模型提交任意字段表达式。 */
    private void applyPackageFilter(LambdaQueryWrapper<CustomerOrder> wrapper, String packageCode) {
        if (!hasText(packageCode)) return;
        String code = packageCode.trim();
        List<ParentPackage> parents = parentPackageMapper.selectList(new LambdaQueryWrapper<ParentPackage>()
            .eq(ParentPackage::getPackageCode, code));
        List<SubPackage> children = subPackageMapper.selectList(new LambdaQueryWrapper<SubPackage>()
            .eq(SubPackage::getSubPackageCode, code));
        Set<Long> parentIds = parents == null ? Collections.emptySet() : parents.stream()
            .map(ParentPackage::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> childIds = children == null ? Collections.emptySet() : children.stream()
            .map(SubPackage::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (parentIds.isEmpty() && childIds.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(query -> {
            if (!parentIds.isEmpty()) query.in(CustomerOrder::getParentPackageId, parentIds);
            if (!childIds.isEmpty()) {
                if (!parentIds.isEmpty()) query.or();
                query.in(CustomerOrder::getChildPackageId, childIds);
            }
        });
    }

    /** 判断订单筛选参数是否包含非空文本。 */
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    /** 去除订单筛选参数首尾空白。 */
    private String trim(String value) { return value == null ? null : value.trim(); }

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

    /** 按订单批量读取核销统计并计算早餐、午晚餐余额。 */
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

    /** 批量加载订单父套餐名称，避免逐订单查询。 */
    private Map<Long, String> parentNames(List<CustomerOrder> orders) {
        Set<Long> ids = orders.stream().map(CustomerOrder::getParentPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return parentPackageMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ParentPackage::getId, ParentPackage::getPackageName));
    }

    /** 批量加载订单子套餐名称，避免逐订单查询。 */
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
        dto.setDealTime(order.getDealTime());
        dto.setCreateTime(order.getCreateTime());
        dto.setStartDate(order.getStartDate());
        dto.setStartMealTypeCode(order.getStartMealType());
        dto.setEndDate(order.getEndDate());
        dto.setMealTypeCode(order.getMealType());
        dto.setScheduleModeCode(order.getScheduleMode());
        dto.setDeliveryDates(parseDeliveryDates(order.getDeliveryDates()));
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

    /** 将订单配送日期 JSON 裁剪为最多 31 个日期，供排餐模式诊断使用。 */
    private List<String> parseDeliveryDates(String value) {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        try {
            List<String> dates = JSON.parseArray(value, String.class);
            return dates == null ? Collections.emptyList() : dates.stream()
                .filter(this::hasText).map(String::trim).distinct().limit(31).collect(Collectors.toList());
        } catch (RuntimeException exception) {
            return Collections.emptyList();
        }
    }

    /** 将订单余额内部 DTO 转换为 Agent 专用余额 DTO。 */
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

    /** 将订单状态代码转换为固定展示名称。 */
    private String statusName(Integer status) {
        if (Integer.valueOf(0).equals(status)) return "已取消";
        if (Integer.valueOf(1).equals(status)) return "进行中";
        if (Integer.valueOf(2).equals(status)) return "已完成";
        if (Integer.valueOf(3).equals(status)) return "已退餐";
        return "未知";
    }
}
