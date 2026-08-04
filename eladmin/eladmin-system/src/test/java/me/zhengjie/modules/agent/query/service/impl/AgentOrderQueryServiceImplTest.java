package me.zhengjie.modules.agent.query.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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

    /** 每个测试显式绑定已验签的全量范围，避免把未绑定上下文误当成授权。 */
    @BeforeEach
    void bindAuthorizedCustomerScope() {
        AgentCustomerDataScopeContext.bind(null);
    }

    /** 清理线程范围，防止复用测试线程时污染其他权限用例。 */
    @AfterEach
    void clearAuthorizedCustomerScope() {
        AgentCustomerDataScopeContext.clear();
    }

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
        order.setDealTime(LocalDateTime.of(2026, 8, 4, 9, 30));
        order.setCreateTime(LocalDateTime.of(2026, 8, 4, 9, 20));
        when(customerOrderMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<CustomerOrder> page = invocation.getArgument(0);
            page.setRecords(Collections.singletonList(order));
            page.setTotal(1L);
            return page;
        });
        when(customerOrderMapper.sumVerifiedCountByOrderIds(Collections.singletonList(9001L))).thenReturn(Collections.<OrderMealVerifiedCountDto>emptyList());
        when(mealVerificationLogMapper.countActiveByOrderIds(Collections.singletonList(9001L))).thenReturn(Collections.singletonList(recordCount(9001L, 3)));
        when(mealRefundLogMapper.countByOrderIds(Collections.singletonList(9001L))).thenReturn(Collections.singletonList(recordCount(9001L, 2)));
        when(mealPlanCustomerMapper.countAllScheduledByOrderIds(Collections.singletonList(9001L))).thenReturn(Collections.singletonList(scheduledCount(9001L, 5)));

        AgentOrderSummaryDto result = service.listByCustomer(1001L, null, 1, 20).getItems().get(0);

        assertEquals(3, result.getVerificationRecordCount());
        assertEquals(2, result.getRefundRecordCount());
        assertEquals(5, result.getMealPlanRecordCount());
        assertEquals(LocalDateTime.of(2026, 8, 4, 9, 30), result.getDealTime());
        assertEquals(LocalDateTime.of(2026, 8, 4, 9, 20), result.getCreateTime());
        verify(mealVerificationLogMapper).countActiveByOrderIds(Collections.singletonList(9001L));
        verify(mealRefundLogMapper).countByOrderIds(Collections.singletonList(9001L));
        verify(mealPlanCustomerMapper).countAllScheduledByOrderIds(Collections.singletonList(9001L));
    }

    /** 不指定客户时只能在已验签数据范围内分页查询，供订单集合追问复用。 */
    @Test
    void shouldListActiveOrdersWithinSignedCustomerScope() {
        AgentCustomerDataScopeContext.bind(Set.of(1001L, 1002L));
        when(customerOrderMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<CustomerOrder> page = invocation.getArgument(0);
            CustomerOrder order = new CustomerOrder();
            order.setId(9001L); order.setCustomerId(1001L); order.setStatus(1);
            page.setRecords(List.of(order));
            page.setTotal(3L);
            return page;
        });
        when(customerOrderMapper.sumVerifiedCountByOrderIds(List.of(9001L))).thenReturn(List.of());
        when(mealVerificationLogMapper.countActiveByOrderIds(List.of(9001L))).thenReturn(List.of());
        when(mealRefundLogMapper.countByOrderIds(List.of(9001L))).thenReturn(List.of());
        when(mealPlanCustomerMapper.countAllScheduledByOrderIds(List.of(9001L))).thenReturn(List.of());

        var result = service.listByCustomer(null, 1, 1, 20);

        assertEquals(3L, result.getTotal());
        assertEquals(1, result.getItems().size());
        assertEquals(1, result.getItems().get(0).getStatusCode());
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
