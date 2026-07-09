package me.zhengjie.modules.agent.service.impl;

import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerInsightRequest;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerMealSummaryResponse;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerOrderSummaryResponse;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerVerificationSummaryResponse;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.domain.MealVerificationLog;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentCustomerInsightServiceImplTest {

    @Mock
    private CustomerProfileService customerProfileService;

    @Mock
    private CustomerOrderMapper customerOrderMapper;

    @Mock
    private MealVerificationLogMapper mealVerificationLogMapper;

    @InjectMocks
    private AgentCustomerInsightServiceImpl service;

    @Test
    void shouldReturnZeroVerificationWhenCustomerHasNoOrders() {
        when(customerProfileService.getDetail(1001L)).thenReturn(customer(1001L, "B3303"));
        when(customerOrderMapper.selectList(any())).thenReturn(List.of());

        AgentCustomerInsightRequest request = new AgentCustomerInsightRequest();
        request.setCustomerId(1001L);

        AgentCustomerVerificationSummaryResponse response = service.getVerificationSummary(request);

        assertEquals(0, response.getTotalVerified());
        assertTrue(response.getRecentVerifications().isEmpty());
    }

    @Test
    void shouldSummarizeOnlyActiveOrdersAndRespectMealType() {
        when(customerProfileService.getDetail(1001L)).thenReturn(customer(1001L, "B3303"));
        CustomerOrder activeOrder = order(11L, 1, 2, 5, 4);
        CustomerOrder finishedOrder = order(12L, 2, 3, 6, 0);
        when(customerOrderMapper.selectList(any())).thenReturn(List.of(activeOrder, finishedOrder));
        when(mealVerificationLogMapper.selectList(any())).thenReturn(List.of(
                verification(11L, "BREAKFAST", 1),
                verification(11L, "LUNCH", 2),
                verification(11L, "DINNER", 1),
                verification(12L, "BREAKFAST", 2)
        ));

        AgentCustomerInsightRequest request = new AgentCustomerInsightRequest();
        request.setCustomerId(1001L);
        request.setMealType("BREAKFAST");

        AgentCustomerMealSummaryResponse response = service.getMealSummary(request);

        assertEquals(2, response.getTotalOrderCount());
        assertEquals(1, response.getActiveOrderCount());
        assertEquals(1, response.getRemainingBreakfast());
        assertEquals(0, response.getRemainingLunchDinner());
        assertEquals(1, response.getVerifiedBreakfast());
        assertEquals(0, response.getVerifiedLunch());
        assertEquals(0, response.getVerifiedDinner());
        assertEquals(1, response.getOrderItems().size());
    }

    @Test
    void shouldCombineLunchAndDinnerIntoSamePool() {
        when(customerProfileService.getDetail(1001L)).thenReturn(customer(1001L, "B3303"));
        CustomerOrder activeOrder = order(11L, 1, 2, 6, 3);
        when(customerOrderMapper.selectList(any())).thenReturn(List.of(activeOrder));
        when(mealVerificationLogMapper.selectList(any())).thenReturn(List.of(
                verification(11L, "LUNCH", 1),
                verification(11L, "DINNER", 2)
        ));

        AgentCustomerInsightRequest request = new AgentCustomerInsightRequest();
        request.setCustomerId(1001L);
        request.setMealType("LUNCH_DINNER");

        AgentCustomerMealSummaryResponse response = service.getMealSummary(request);

        assertEquals(0, response.getRemainingBreakfast());
        assertEquals(3, response.getRemainingLunchDinner());
        assertEquals(0, response.getVerifiedBreakfast());
        assertEquals(1, response.getVerifiedLunch());
        assertEquals(2, response.getVerifiedDinner());
    }

    @Test
    void shouldFilterOrdersByStatusForOrderSummary() {
        when(customerProfileService.getDetail(1001L)).thenReturn(customer(1001L, "B3303"));
        CustomerOrder activeOrder = order(11L, 1, 2, 5, 4);
        when(customerOrderMapper.selectList(any())).thenReturn(List.of(activeOrder));
        when(mealVerificationLogMapper.selectList(any())).thenReturn(List.of());

        AgentCustomerInsightRequest request = new AgentCustomerInsightRequest();
        request.setCustomerId(1001L);
        request.setOrderStatus(1);

        AgentCustomerOrderSummaryResponse response = service.getOrderSummary(request);

        assertEquals(1, response.getOrders().size());
        assertEquals(Integer.valueOf(1), response.getOrders().get(0).getStatus());
    }

    @Test
    void shouldReturnPresentFalseWhenCustomerMissing() {
        when(customerProfileService.getDetail(1001L)).thenReturn(null);

        AgentCustomerInsightRequest request = new AgentCustomerInsightRequest();
        request.setCustomerId(1001L);
        request.setCustomerCode("B3303");

        AgentCustomerMealSummaryResponse response = service.getMealSummary(request);

        assertFalse(response.isPresent());
        assertEquals("B3303", response.getCustomerCode());
    }

    private CustomerProfileDetailDto customer(Long id, String code) {
        CustomerProfileDetailDto dto = new CustomerProfileDetailDto();
        dto.setId(id);
        dto.setCustomerCode(code);
        dto.setCustomerName("张三");
        return dto;
    }

    private CustomerOrder order(Long id, Integer status, Integer breakfastCount, Integer lunchDinnerCount, Integer remainingCount) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setCustomerId(1001L);
        order.setOrderCode("ORD-" + id);
        order.setStatus(status);
        order.setBreakfastCount(breakfastCount);
        order.setLunchDinnerCount(lunchDinnerCount);
        order.setRemainingCount(remainingCount);
        order.setCreateTime(LocalDateTime.of(2026, 7, 9, 10, 0));
        return order;
    }

    private MealVerificationLog verification(Long orderId, String mealType, Integer count) {
        MealVerificationLog log = new MealVerificationLog();
        log.setOrderId(orderId);
        log.setMealType(mealType);
        log.setVerificationCount(count);
        log.setDeleted(0);
        return log;
    }
}
