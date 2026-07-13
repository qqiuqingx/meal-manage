package me.zhengjie.modules.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.agent.domain.dto.insight.*;
import me.zhengjie.modules.agent.service.AgentCustomerInsightService;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealBalanceDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.service.OrderMealBalanceCalculator;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.domain.MealVerificationLog;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import me.zhengjie.utils.PageResult;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户信息查询聚合服务实现
 * 为智能排查 Agent 提供客户维度的确定性业务数据查询
 * @author qqx
 * @date 2026-07-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCustomerInsightServiceImpl implements AgentCustomerInsightService {

    private final CustomerProfileService customerProfileService;
    private final CustomerOrderMapper customerOrderMapper;
    private final MealVerificationLogMapper mealVerificationLogMapper;

    @Override
    public AgentCustomerMealSummaryResponse getMealSummary(AgentCustomerInsightRequest request) {
        long startTime = System.currentTimeMillis();

        CustomerProfileDetailDto customer = resolveCustomer(request);
        if (customer == null) {
            AgentCustomerMealSummaryResponse resp = new AgentCustomerMealSummaryResponse();
            resp.setPresent(false);
            resp.setCustomerCode(request.getCustomerCode());
            log.info("getMealSummary: customer not found, code={}, cost={}ms",
                    request.getCustomerCode(), System.currentTimeMillis() - startTime);
            return resp;
        }

        List<CustomerOrder> allOrders = queryCustomerOrders(customer.getId(), null);
        List<CustomerOrder> activeOrders = allOrders.stream()
                .filter(OrderMealBalanceCalculator::isActiveOrder)
                .collect(Collectors.toList());
        Map<Long, List<MealVerificationLog>> verificationByOrder = loadVerificationMap(activeOrders);

        AgentCustomerMealSummaryResponse response = new AgentCustomerMealSummaryResponse();
        response.setCustomerId(customer.getId());
        response.setCustomerCode(customer.getCustomerCode());
        response.setCustomerName(customer.getCustomerName());
        response.setTotalOrderCount(allOrders.size());
        response.setActiveOrderCount(activeOrders.size());

        int totalRemainingBreakfast = 0;
        int totalRemainingLunchDinner = 0;
        int totalVerifiedBreakfast = 0;
        int totalVerifiedLunch = 0;
        int totalVerifiedDinner = 0;

        List<AgentCustomerOrderMealBalanceItem> orderItems = new ArrayList<>();
        for (CustomerOrder order : activeOrders) {
            OrderMealBalanceDto orderStats = calculateOrderMealStats(order, verificationByOrder.getOrDefault(order.getId(), Collections.emptyList()));
            AgentCustomerOrderMealBalanceItem item = buildOrderMealBalanceItem(order, orderStats);
            orderItems.add(item);

            if (isBreakfastOnly(request.getMealType())) {
                totalRemainingBreakfast += orderStats.getRemainingBreakfast();
                totalVerifiedBreakfast += orderStats.getVerifiedBreakfast();
            } else if (isLunchDinnerOnly(request.getMealType())) {
                totalRemainingLunchDinner += orderStats.getRemainingLunchDinner();
                totalVerifiedLunch += orderStats.getVerifiedLunch();
                totalVerifiedDinner += orderStats.getVerifiedDinner();
            } else {
                totalRemainingBreakfast += orderStats.getRemainingBreakfast();
                totalRemainingLunchDinner += orderStats.getRemainingLunchDinner();
                totalVerifiedBreakfast += orderStats.getVerifiedBreakfast();
                totalVerifiedLunch += orderStats.getVerifiedLunch();
                totalVerifiedDinner += orderStats.getVerifiedDinner();
            }
        }

        response.setOrderItems(orderItems);
        response.setRemainingBreakfast(totalRemainingBreakfast);
        response.setRemainingLunchDinner(totalRemainingLunchDinner);
        response.setTotalRemaining(totalRemainingBreakfast + totalRemainingLunchDinner);
        response.setVerifiedBreakfast(totalVerifiedBreakfast);
        response.setVerifiedLunch(totalVerifiedLunch);
        response.setVerifiedDinner(totalVerifiedDinner);
        response.setTotalVerified(totalVerifiedBreakfast + totalVerifiedLunch + totalVerifiedDinner);

        log.info("getMealSummary: customerId={}, code={}, orders={}, activeOrders={}, cost={}ms",
                customer.getId(), customer.getCustomerCode(), allOrders.size(), activeOrders.size(),
                System.currentTimeMillis() - startTime);

        return response;
    }

    @Override
    public AgentCustomerVerificationSummaryResponse getVerificationSummary(AgentCustomerInsightRequest request) {
        long startTime = System.currentTimeMillis();

        CustomerProfileDetailDto customer = resolveCustomer(request);
        if (customer == null) {
            AgentCustomerVerificationSummaryResponse resp = new AgentCustomerVerificationSummaryResponse();
            resp.setPresent(false);
            resp.setCustomerCode(request.getCustomerCode());
            log.info("getVerificationSummary: customer not found, code={}, cost={}ms",
                    request.getCustomerCode(), System.currentTimeMillis() - startTime);
            return resp;
        }

        List<CustomerOrder> allOrders = queryCustomerOrders(customer.getId(), null);
        List<Long> orderIds = allOrders.stream()
                .map(CustomerOrder::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        AgentCustomerVerificationSummaryResponse response = new AgentCustomerVerificationSummaryResponse();
        response.setCustomerId(customer.getId());
        response.setCustomerCode(customer.getCustomerCode());
        response.setCustomerName(customer.getCustomerName());
        if (orderIds.isEmpty()) {
            response.setRecentVerifications(Collections.emptyList());
            log.info("getVerificationSummary: customerId={} code={} totalVerified=0 cost={}ms",
                    customer.getId(), customer.getCustomerCode(), System.currentTimeMillis() - startTime);
            return response;
        }

        LambdaQueryWrapper<MealVerificationLog> queryWrapper = new LambdaQueryWrapper<MealVerificationLog>()
                .in(MealVerificationLog::getOrderId, orderIds)
                .eq(MealVerificationLog::getDeleted, 0);

        if (request.getMealType() != null && !"LUNCH_DINNER".equals(request.getMealType())) {
            queryWrapper.eq(MealVerificationLog::getMealType, request.getMealType());
        }

        queryWrapper.orderByDesc(MealVerificationLog::getCreateTime);

        List<MealVerificationLog> verifications = mealVerificationLogMapper.selectList(queryWrapper);

        // Java端日期过滤（recordDate 为 java.util.Date 类型）
        String dateStart = request.getRecordDateStart();
        String dateEnd = request.getRecordDateEnd();
        if (dateStart != null || dateEnd != null) {
            verifications = verifications.stream()
                    .filter(v -> v.getRecordDate() != null)
                    .filter(v -> dateStart == null || formatDate(v.getRecordDate()).compareTo(dateStart) >= 0)
                    .filter(v -> dateEnd == null || formatDate(v.getRecordDate()).compareTo(dateEnd) <= 0)
                    .collect(Collectors.toList());
        }

        int limit = request.getRecentLimit() != null ? request.getRecentLimit() : 10;
        List<MealVerificationLog> recentVerifications = verifications.size() > limit
                ? verifications.subList(0, limit) : verifications;

        Map<Long, String> orderNoMap = allOrders.stream()
                .collect(Collectors.toMap(CustomerOrder::getId, CustomerOrder::getOrderCode, (a, b) -> a));

        int totalBf = sumVerificationCount(verifications, "BREAKFAST");
        int totalLunch = sumVerificationCount(verifications, "LUNCH");
        int totalDinner = sumVerificationCount(verifications, "DINNER");

        response.setTotalVerifiedBreakfast(totalBf);
        response.setTotalVerifiedLunch(totalLunch);
        response.setTotalVerifiedDinner(totalDinner);
        response.setTotalVerified(totalBf + totalLunch + totalDinner);

        List<AgentCustomerVerificationLogItem> items = recentVerifications.stream()
                .map(v -> {
                    AgentCustomerVerificationLogItem item = new AgentCustomerVerificationLogItem();
                    item.setId(v.getId());
                    item.setOrderId(v.getOrderId());
                    item.setOrderNo(orderNoMap.get(v.getOrderId()));
                    item.setMealType(v.getMealType());
                    item.setVerificationCount(v.getVerificationCount());
                    item.setRecordDate(v.getRecordDate());
                    item.setCreateTime(v.getCreateTime());
                    item.setDeleted(v.getDeleted());
                    return item;
                })
                .collect(Collectors.toList());
        response.setRecentVerifications(items);

        log.info("getVerificationSummary: customerId={}, code={}, totalVerified={}, cost={}ms",
                customer.getId(), customer.getCustomerCode(), response.getTotalVerified(),
                System.currentTimeMillis() - startTime);

        return response;
    }

    @Override
    public AgentCustomerOrderSummaryResponse getOrderSummary(AgentCustomerInsightRequest request) {
        long startTime = System.currentTimeMillis();

        CustomerProfileDetailDto customer = resolveCustomer(request);
        if (customer == null) {
            AgentCustomerOrderSummaryResponse resp = new AgentCustomerOrderSummaryResponse();
            resp.setPresent(false);
            resp.setCustomerCode(request.getCustomerCode());
            log.info("getOrderSummary: customer not found, code={}, cost={}ms",
                    request.getCustomerCode(), System.currentTimeMillis() - startTime);
            return resp;
        }

        List<CustomerOrder> orders = queryCustomerOrders(customer.getId(), request.getOrderStatus());
        final Map<Long, List<MealVerificationLog>> verificationMap = loadVerificationMap(orders);

        List<AgentCustomerOrderMealBalanceItem> orderItems = orders.stream()
                .map(order -> buildOrderMealBalanceItem(order,
                        calculateOrderMealStats(order, verificationMap.getOrDefault(order.getId(), Collections.emptyList()))))
                .collect(Collectors.toList());

        AgentCustomerOrderSummaryResponse response = new AgentCustomerOrderSummaryResponse();
        response.setCustomerId(customer.getId());
        response.setCustomerCode(customer.getCustomerCode());
        response.setCustomerName(customer.getCustomerName());
        response.setOrders(orderItems);

        log.info("getOrderSummary: customerId={}, code={}, orders={}, cost={}ms",
                customer.getId(), customer.getCustomerCode(), orders.size(),
                System.currentTimeMillis() - startTime);

        return response;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 解析客户：优先按 customerId，其次按 customerCode
     */
    private CustomerProfileDetailDto resolveCustomer(AgentCustomerInsightRequest request) {
        if (request.getCustomerId() != null) {
            return customerProfileService.getDetail(request.getCustomerId());
        }
        if (request.getCustomerCode() != null) {
            CustomerProfileQueryCriteria criteria = new CustomerProfileQueryCriteria();
            criteria.setCustomerCode(request.getCustomerCode());
            criteria.setPage(0);
            criteria.setSize(1);

            PageResult<?> pageResult = customerProfileService.queryAll(criteria,
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1));
            if (pageResult == null || pageResult.getContent() == null || pageResult.getContent().isEmpty()) {
                return null;
            }

            Object item = pageResult.getContent().get(0);
            if (!(item instanceof CustomerProfile)) {
                return null;
            }
            CustomerProfile profile = (CustomerProfile) item;
            return customerProfileService.getDetail(profile.getId());
        }
        return null;
    }

    private int sumVerificationCount(List<MealVerificationLog> logs, String mealType) {
        return logs.stream()
                .filter(v -> mealType.equals(v.getMealType()))
                .filter(v -> v.getVerificationCount() != null)
                .mapToInt(MealVerificationLog::getVerificationCount)
                .sum();
    }

    /**
     * 按客户和状态查询订单列表，统一排序，避免各个聚合口径出现偏差。
     *
     * @param customerId 客户 ID
     * @param orderStatus 订单状态，可为空
     * @return 订单列表
     */
    private List<CustomerOrder> queryCustomerOrders(Long customerId, Integer orderStatus) {
        LambdaQueryWrapper<CustomerOrder> queryWrapper = new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getCustomerId, customerId);
        if (orderStatus != null) {
            queryWrapper.eq(CustomerOrder::getStatus, orderStatus);
        }
        queryWrapper.orderByDesc(CustomerOrder::getCreateTime);
        return customerOrderMapper.selectList(queryWrapper);
    }

    /**
     * 批量加载订单对应的未删除核销日志，保证所有聚合都限定在当前客户订单集合内。
     *
     * @param orders 订单列表
     * @return 按订单 ID 分组的核销日志
     */
    private Map<Long, List<MealVerificationLog>> loadVerificationMap(List<CustomerOrder> orders) {
        List<Long> orderIds = orders.stream()
                .map(CustomerOrder::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MealVerificationLog> allVerifications = mealVerificationLogMapper.selectList(
                new LambdaQueryWrapper<MealVerificationLog>()
                        .in(MealVerificationLog::getOrderId, orderIds)
                        .eq(MealVerificationLog::getDeleted, 0)
        );
        return allVerifications.stream().collect(Collectors.groupingBy(MealVerificationLog::getOrderId));
    }

    /**
     * 计算单笔订单的早餐池和午晚餐池统计，避免多处重复实现业务口径。
     *
     * @param order 订单
     * @param orderVerifications 该订单核销日志
     * @return 订单餐数统计
     */
    private OrderMealBalanceDto calculateOrderMealStats(CustomerOrder order, List<MealVerificationLog> orderVerifications) {
        int verifiedBf = sumVerificationCount(orderVerifications, "BREAKFAST");
        int verifiedLunch = sumVerificationCount(orderVerifications, "LUNCH");
        int verifiedDinner = sumVerificationCount(orderVerifications, "DINNER");
        return OrderMealBalanceCalculator.calculate(order, verifiedBf, verifiedLunch, verifiedDinner);
    }

    /**
     * 构建订单明细响应，前端直接复用这份结构展示余额和历史数据。
     *
     * @param order 订单
     * @param orderStats 订单餐数统计
     * @return 订单明细 DTO
     */
    private AgentCustomerOrderMealBalanceItem buildOrderMealBalanceItem(CustomerOrder order, OrderMealBalanceDto orderStats) {
        AgentCustomerOrderMealBalanceItem item = new AgentCustomerOrderMealBalanceItem();
        item.setOrderId(order.getId());
        item.setOrderNo(order.getOrderCode());
        item.setStatus(order.getStatus());
        item.setBreakfastCount(getBreakfastCount(order));
        item.setLunchDinnerCount(getLunchDinnerCount(order));
        item.setVerifiedBreakfast(orderStats.getVerifiedBreakfast());
        item.setVerifiedLunch(orderStats.getVerifiedLunch());
        item.setVerifiedDinner(orderStats.getVerifiedDinner());
        item.setRemainingBreakfast(orderStats.getRemainingBreakfast());
        item.setRemainingLunchDinner(orderStats.getRemainingLunchDinner());
        item.setStartDate(order.getStartDate());
        item.setEndDate(order.getEndDate());
        item.setCreateTime(order.getCreateTime());
        return item;
    }

    private int getBreakfastCount(CustomerOrder order) {
        Integer count = order.getBreakfastCount();
        return count != null ? count : 0;
    }

    private int getLunchDinnerCount(CustomerOrder order) {
        Integer count = order.getLunchDinnerCount();
        return count != null ? count : 0;
    }

    private String formatDate(Date date) {
        if (date == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    private boolean isBreakfastOnly(String mealType) {
        return "BREAKFAST".equals(mealType);
    }

    private boolean isLunchDinnerOnly(String mealType) {
        return "LUNCH".equals(mealType)
                || "DINNER".equals(mealType)
                || "LUNCH_DINNER".equals(mealType);
    }

}
