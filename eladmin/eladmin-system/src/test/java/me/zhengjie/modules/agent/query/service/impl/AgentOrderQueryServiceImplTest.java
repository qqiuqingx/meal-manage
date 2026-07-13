package me.zhengjie.modules.agent.query.service.impl;

import me.zhengjie.modules.agent.query.domain.dto.AgentOrderSummaryDto;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealVerifiedCountDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.meal.domain.dto.OrderAssociatedRecordCountDto;
import me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealRefundLogMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 订单详情必须绑定当前客户上下文，防止按猜测订单 ID 越权。 */
@ExtendWith(MockitoExtension.class)
class AgentOrderQueryServiceImplTest {
    @Mock private CustomerOrderMapper customerOrderMapper;
    @Mock private ParentPackageMapper parentPackageMapper;
    @Mock private SubPackageMapper subPackageMapper;
    @Mock private MealVerificationLogMapper mealVerificationLogMapper;
    @Mock private MealRefundLogMapper mealRefundLogMapper;
    @Mock private MealPlanCustomerMapper mealPlanCustomerMapper;
    @InjectMocks private AgentOrderQueryServiceImpl service;

    @Test
    void shouldNotReturnOrderThatBelongsToAnotherCustomer() {
        CustomerOrder order = new CustomerOrder(); order.setId(9001L); order.setCustomerId(2002L); order.setOrderCode("O9001");
        when(customerOrderMapper.selectById(9001L)).thenReturn(order);

        assertNull(service.getDetail(9001L, null, 1001L));

        verify(customerOrderMapper).selectById(9001L);
        verifyNoInteractions(parentPackageMapper, subPackageMapper);
    }

    @Test
    void shouldBatchAttachAssociatedRecordCountsWithoutReadingAmounts() {
        CustomerOrder order = new CustomerOrder();
        order.setId(9001L); order.setCustomerId(1001L); order.setOrderCode("O9001");
        order.setBreakfastCount(6); order.setLunchDinnerCount(12);
        when(customerOrderMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(Collections.singletonList(order));
        when(customerOrderMapper.sumVerifiedCountByOrderIds(Collections.singletonList(9001L))).thenReturn(Collections.<OrderMealVerifiedCountDto>emptyList());
        when(mealVerificationLogMapper.countActiveByOrderIds(Collections.singletonList(9001L))).thenReturn(Collections.singletonList(recordCount(9001L, 3)));
        when(mealRefundLogMapper.countByOrderIds(Collections.singletonList(9001L))).thenReturn(Collections.singletonList(recordCount(9001L, 2)));
        when(mealPlanCustomerMapper.countAllScheduledByOrderIds(Collections.singletonList(9001L))).thenReturn(Collections.singletonList(scheduledCount(9001L, 5)));

        AgentOrderSummaryDto result = service.listByCustomer(1001L, null, 1, 20).getItems().get(0);

        assertEquals(3, result.getVerificationRecordCount());
        assertEquals(2, result.getRefundRecordCount());
        assertEquals(5, result.getMealPlanRecordCount());
        verify(mealVerificationLogMapper).countActiveByOrderIds(Collections.singletonList(9001L));
        verify(mealRefundLogMapper).countByOrderIds(Collections.singletonList(9001L));
        verify(mealPlanCustomerMapper).countAllScheduledByOrderIds(Collections.singletonList(9001L));
    }

    private OrderAssociatedRecordCountDto recordCount(Long orderId, int count) {
        OrderAssociatedRecordCountDto dto = new OrderAssociatedRecordCountDto();
        dto.setOrderId(orderId); dto.setRecordCount(count); return dto;
    }

    private OrderScheduledCountDto scheduledCount(Long orderId, int count) {
        OrderScheduledCountDto dto = new OrderScheduledCountDto();
        dto.setOrderId(orderId); dto.setScheduledCount(count); return dto;
    }
}
