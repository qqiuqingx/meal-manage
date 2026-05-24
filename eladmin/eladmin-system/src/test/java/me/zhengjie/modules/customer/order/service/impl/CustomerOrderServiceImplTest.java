package me.zhengjie.modules.customer.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceImplTest {

    @Mock
    private CustomerOrderMapper orderMapper;

    @Mock
    private MealPlanCustomerMapper mealPlanCustomerMapper;

    @InjectMocks
    private CustomerOrderServiceImpl orderService;

    @Test
    void query_setsEstimatedRemainingCountFromCurrentRemainingMinusTodayUnverifiedPlans() {
        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setBreakfastCount(3);
        order.setLunchDinnerCount(7);
        order.setRemainingCount(6);

        OrderScheduledCountDto scheduledCount = new OrderScheduledCountDto();
        scheduledCount.setOrderId(10L);
        scheduledCount.setScheduledCount(2);

        when(orderMapper.findAll(any(CustomerOrderQueryCriteria.class), any(Page.class)))
                .thenReturn(Collections.singletonList(order));
        when(mealPlanCustomerMapper.countTodayUnverifiedScheduledByOrderIds(eq(Collections.singletonList(10L)), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(scheduledCount));

        PageResult<?> result = orderService.query(new CustomerOrderQueryCriteria(), 1, 10);

        @SuppressWarnings("unchecked")
        List<CustomerOrder> orders = (List<CustomerOrder>) result.getContent();
        assertEquals(1, orders.size());
        assertEquals(10, orders.get(0).getTotalCount());
        assertEquals(4, orders.get(0).getEstimatedRemainingCount());

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(mealPlanCustomerMapper)
                .countTodayUnverifiedScheduledByOrderIds(eq(Collections.singletonList(10L)), dateCaptor.capture());
        assertNotNull(dateCaptor.getValue());
    }
}
