package me.zhengjie.modules.agent.query.service.impl;

import me.zhengjie.modules.agent.query.domain.dto.AgentDailyCustomerStatsDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationDailyRequest;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealVerifiedCountDto;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 运营统计必须按客户+餐次对比应服务集合与成功排餐集合。 */
class AgentOperationQueryServiceImplTest {

    @Test
    void shouldCalculateExpectedAndUnscheduledCustomersByCustomerMeal() {
        MealPlanMapper planMapper = mock(MealPlanMapper.class);
        MealPlanCustomerMapper customerMapper = mock(MealPlanCustomerMapper.class);
        CustomerOrderMapper orderMapper = mock(CustomerOrderMapper.class);
        ParentPackageMapper parentPackageMapper = mock(ParentPackageMapper.class);
        MealPlanService mealPlanService = mock(MealPlanService.class);
        MealPlan plan = new MealPlan(); plan.setId(10L); plan.setMealType("LUNCH");
        MealPlanCustomer scheduled = new MealPlanCustomer(); scheduled.setCustomerId(1L); scheduled.setStatus(1); scheduled.setIsVerified(0);
        when(planMapper.selectList(any())).thenReturn(List.of(plan));
        when(customerMapper.selectByMealPlanId(10L)).thenReturn(List.of(scheduled));
        when(mealPlanService.findExpectedCustomerOrders(eq(java.time.LocalDate.of(2026, 7, 13)), eq("LUNCH"))).thenReturn(List.of(order(1L), order(2L)));
        AgentOperationQueryServiceImpl service = new AgentOperationQueryServiceImpl(planMapper, customerMapper, orderMapper, parentPackageMapper, mealPlanService);
        AgentOperationDailyRequest request = new AgentOperationDailyRequest(); request.setRecordDate("2026-07-13"); request.setMealType("LUNCH");

        AgentDailyCustomerStatsDto result = service.dailyCustomers(request);

        assertEquals(1L, result.getScheduledCustomerCount());
        assertEquals(2L, result.getExpectedCustomerCount());
        assertEquals(1L, result.getUnscheduledCustomerCount());
        assertEquals(1L, result.getUnverifiedCustomerCount());
        assertEquals(1L, result.getMetricMealTypeBreakdown().get("DAILY_SCHEDULED_CUSTOMER_COUNT").get("LUNCH"));
        assertEquals(2L, result.getMetricMealTypeBreakdown().get("DAILY_EXPECTED_CUSTOMER_COUNT").get("LUNCH"));
        assertEquals(1L, result.getMetricMealTypeBreakdown().get("DAILY_UNSCHEDULED_CUSTOMER_COUNT").get("LUNCH"));
    }

    /** 跨客户统计必须在聚合前按签名访问上下文限定客户集合。 */
    @Test
    void shouldApplyCustomerDataScopeBeforeAggregatingDailyWorkload() {
        MealPlanMapper planMapper = mock(MealPlanMapper.class);
        MealPlanCustomerMapper customerMapper = mock(MealPlanCustomerMapper.class);
        CustomerOrderMapper orderMapper = mock(CustomerOrderMapper.class);
        ParentPackageMapper parentPackageMapper = mock(ParentPackageMapper.class);
        MealPlanService mealPlanService = mock(MealPlanService.class);
        MealPlan plan = new MealPlan(); plan.setId(10L); plan.setMealType("LUNCH");
        MealPlanCustomer allowed = new MealPlanCustomer(); allowed.setCustomerId(1L); allowed.setStatus(1); allowed.setIsVerified(0);
        MealPlanCustomer denied = new MealPlanCustomer(); denied.setCustomerId(2L); denied.setStatus(1); denied.setIsVerified(0);
        when(planMapper.selectList(any())).thenReturn(List.of(plan));
        when(customerMapper.selectByMealPlanId(10L)).thenReturn(List.of(allowed, denied));
        when(mealPlanService.findExpectedCustomerOrders(eq(java.time.LocalDate.of(2026, 7, 13)), eq("LUNCH"))).thenReturn(List.of(order(1L), order(2L)));
        AgentOperationQueryServiceImpl service = new AgentOperationQueryServiceImpl(planMapper, customerMapper, orderMapper, parentPackageMapper, mealPlanService);
        AgentOperationDailyRequest request = new AgentOperationDailyRequest(); request.setRecordDate("2026-07-13"); request.setMealType("LUNCH");

        AgentCustomerDataScopeContext.bind(Set.of(1L));
        try {
            AgentDailyCustomerStatsDto result = service.dailyCustomers(request);

            assertEquals(1L, result.getScheduledCustomerCount());
            assertEquals(1L, result.getExpectedCustomerCount());
            assertEquals(0L, result.getUnscheduledCustomerCount());
            assertEquals(1L, result.getUnverifiedCustomerCount());
        } finally {
            AgentCustomerDataScopeContext.clear();
        }
    }

    /** 活跃客户以订单与未删除核销的实时余额为准，不能依赖订单冗余余额字段。 */
    @Test
    void shouldCalculateActiveCustomersFromRealtimeMealBalance() {
        MealPlanMapper planMapper = mock(MealPlanMapper.class);
        MealPlanCustomerMapper customerMapper = mock(MealPlanCustomerMapper.class);
        CustomerOrderMapper orderMapper = mock(CustomerOrderMapper.class);
        ParentPackageMapper parentPackageMapper = mock(ParentPackageMapper.class);
        MealPlanService mealPlanService = mock(MealPlanService.class);
        CustomerOrder exhausted = order(10L, 1L, 1, 0);
        CustomerOrder available = order(11L, 2L, 2, 0);
        when(orderMapper.selectList(any())).thenReturn(List.of(exhausted, available));
        when(orderMapper.sumVerifiedCountByOrderIds(List.of(10L, 11L))).thenReturn(List.of(verified(10L, "BREAKFAST", 1)));
        AgentOperationQueryServiceImpl service = new AgentOperationQueryServiceImpl(planMapper, customerMapper, orderMapper, parentPackageMapper, mealPlanService);

        assertEquals(1L, service.activeCustomers().getTotal());
    }

    /** 套餐和来源分组必须复用有效订单集合，待排餐仅保留尚无成功排餐的客户餐次。 */
    @Test
    void shouldGroupDailyMetricsByPackageAndCustomerSource() {
        MealPlanMapper planMapper = mock(MealPlanMapper.class);
        MealPlanCustomerMapper customerMapper = mock(MealPlanCustomerMapper.class);
        CustomerOrderMapper orderMapper = mock(CustomerOrderMapper.class);
        ParentPackageMapper parentPackageMapper = mock(ParentPackageMapper.class);
        MealPlanService mealPlanService = mock(MealPlanService.class);
        MealPlan plan = new MealPlan(); plan.setId(10L); plan.setMealType("LUNCH");
        CustomerOrder scheduledOrder = expectedOrder(11L, 1L, 101L, "抖音");
        CustomerOrder pendingOrder = expectedOrder(12L, 2L, 102L, "客户介绍");
        MealPlanCustomer scheduled = new MealPlanCustomer();
        scheduled.setCustomerId(1L); scheduled.setOrderId(11L); scheduled.setParentPackageId(101L); scheduled.setStatus(1); scheduled.setIsVerified(0);
        when(planMapper.selectList(any())).thenReturn(List.of(plan));
        when(customerMapper.selectByMealPlanId(10L)).thenReturn(List.of(scheduled));
        when(orderMapper.selectBatchIds(any())).thenReturn(List.of(scheduledOrder));
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(List.of(parentPackage(101L, "轻食套餐"), parentPackage(102L, "营养套餐")));
        when(mealPlanService.findExpectedCustomerOrders(eq(java.time.LocalDate.of(2026, 7, 13)), eq("LUNCH"))).thenReturn(List.of(scheduledOrder, pendingOrder));
        AgentOperationQueryServiceImpl service = new AgentOperationQueryServiceImpl(planMapper, customerMapper, orderMapper, parentPackageMapper, mealPlanService);

        AgentOperationDailyRequest packageRequest = new AgentOperationDailyRequest();
        packageRequest.setRecordDate("2026-07-13"); packageRequest.setMealType("LUNCH"); packageRequest.setDimensions(List.of("PACKAGE"));
        AgentDailyCustomerStatsDto packageResult = service.dailyCustomers(packageRequest);

        assertEquals(1L, packageResult.getMetricDimensionBreakdown().get("DAILY_SCHEDULED_CUSTOMER_COUNT").get("轻食套餐"));
        assertEquals(1L, packageResult.getMetricDimensionBreakdown().get("DAILY_EXPECTED_CUSTOMER_COUNT").get("轻食套餐"));
        assertEquals(1L, packageResult.getMetricDimensionBreakdown().get("DAILY_EXPECTED_CUSTOMER_COUNT").get("营养套餐"));
        assertEquals(1L, packageResult.getMetricDimensionBreakdown().get("DAILY_UNSCHEDULED_CUSTOMER_COUNT").get("营养套餐"));

        AgentOperationDailyRequest sourceRequest = new AgentOperationDailyRequest();
        sourceRequest.setRecordDate("2026-07-13"); sourceRequest.setMealType("LUNCH"); sourceRequest.setDimensions(List.of("CUSTOMER_SOURCE"));
        AgentDailyCustomerStatsDto sourceResult = service.dailyCustomers(sourceRequest);

        assertEquals(1L, sourceResult.getMetricDimensionBreakdown().get("DAILY_SCHEDULED_CUSTOMER_COUNT").get("抖音"));
        assertEquals(1L, sourceResult.getMetricDimensionBreakdown().get("DAILY_UNSCHEDULED_CUSTOMER_COUNT").get("客户介绍"));

        AgentOperationDailyRequest combinedRequest = new AgentOperationDailyRequest();
        combinedRequest.setRecordDate("2026-07-13"); combinedRequest.setMealType("LUNCH"); combinedRequest.setDimensions(List.of("MEAL_TYPE", "PACKAGE"));
        AgentDailyCustomerStatsDto combinedResult = service.dailyCustomers(combinedRequest);

        assertEquals(1L, combinedResult.getMetricDimensionBreakdown().get("DAILY_SCHEDULED_CUSTOMER_COUNT").get("LUNCH / 轻食套餐"));
        assertEquals(1L, combinedResult.getMetricDimensionBreakdown().get("DAILY_UNSCHEDULED_CUSTOMER_COUNT").get("LUNCH / 营养套餐"));
    }

    private CustomerOrder order(Long id, Long customerId, int breakfast, int lunchDinner) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id); order.setCustomerId(customerId); order.setStatus(1);
        order.setBreakfastCount(breakfast); order.setLunchDinnerCount(lunchDinner); return order;
    }

    /** 构造仅用于应服务客户口径测试的订单，订单 ID 与客户 ID 固定一致。 */
    private CustomerOrder order(Long customerId) {
        CustomerOrder order = new CustomerOrder();
        order.setId(customerId); order.setCustomerId(customerId); return order;
    }

    /** 构造携带套餐和来源归属的有效订单，供运营维度口径测试复用。 */
    private CustomerOrder expectedOrder(Long id, Long customerId, Long parentPackageId, String customerSource) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id); order.setCustomerId(customerId); order.setParentPackageId(parentPackageId); order.setCustomerSource(customerSource); return order;
    }

    /** 构造仅包含可展示名称的父套餐主数据。 */
    private ParentPackage parentPackage(Long id, String name) {
        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(id); parentPackage.setPackageName(name); return parentPackage;
    }

    private OrderMealVerifiedCountDto verified(Long orderId, String mealType, int count) {
        OrderMealVerifiedCountDto dto = new OrderMealVerifiedCountDto();
        dto.setOrderId(orderId); dto.setMealType(mealType); dto.setVerifiedCount(count); return dto;
    }
}
