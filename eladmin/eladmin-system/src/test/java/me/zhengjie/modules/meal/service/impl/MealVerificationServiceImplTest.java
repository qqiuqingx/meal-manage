package me.zhengjie.modules.meal.service.impl;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealVerificationServiceImplTest {

    @Mock
    private MealVerificationLogMapper verificationLogMapper;

    @Mock
    private MealPlanCustomerMapper mealPlanCustomerMapper;

    @Mock
    private MealPlanMapper mealPlanMapper;

    @Mock
    private CustomerOrderMapper customerOrderMapper;

    @InjectMocks
    private MealVerificationServiceImpl service;

    @Test
    void shouldVerifyEachServingAsOneIndependentMeal() {
        MealPlanCustomer firstServing = buildServing(101L, 1);
        MealPlanCustomer secondServing = buildServing(102L, 2);
        Map<Long, MealPlanCustomer> servings = new HashMap<>();
        servings.put(firstServing.getId(), firstServing);
        servings.put(secondServing.getId(), secondServing);

        MealPlan plan = new MealPlan();
        plan.setId(20L);
        plan.setRecordDate(LocalDate.of(2026, 9, 25));
        plan.setMealType("LUNCH");

        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setStatus(1);
        order.setRemainingCount(2);
        order.setVerifiedCount(0);
        order.setLunchDinnerPrice(new BigDecimal("45.00"));

        when(mealPlanCustomerMapper.selectById(anyLong())).thenAnswer(invocation -> servings.get(invocation.getArgument(0)));
        when(mealPlanMapper.selectById(20L)).thenReturn(plan);
        when(customerOrderMapper.selectById(10L)).thenReturn(order);
        when(mealPlanCustomerMapper.markVerifiedIfPending(anyLong(), any(Date.class), eq("tester"))).thenReturn(1);
        when(customerOrderMapper.incrementVerifiedCountAndAmount(10L, new BigDecimal("45.00"))).thenAnswer(invocation -> {
            order.setRemainingCount(order.getRemainingCount() - 1);
            order.setVerifiedCount(order.getVerifiedCount() + 1);
            return 1;
        });
        when(customerOrderMapper.updateStatusToCompletedWhenFinished(10L)).thenReturn(0);

        MealVerificationDto request = new MealVerificationDto();
        request.setCustomerPlanIds(Arrays.asList(firstServing.getId(), secondServing.getId()));
        request.setRemark("分份核销");

        MealVerificationResultDto result = service.verify(request, "tester");

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertEquals(0, order.getRemainingCount());
        ArgumentCaptor<MealVerificationLog> logs = ArgumentCaptor.forClass(MealVerificationLog.class);
        verify(verificationLogMapper, times(2)).insert(logs.capture());
        assertEquals(Arrays.asList(101L, 102L), Arrays.asList(
                logs.getAllValues().get(0).getMealPlanCustomerId(),
                logs.getAllValues().get(1).getMealPlanCustomerId()));
        assertEquals(Arrays.asList(1, 1), Arrays.asList(
                logs.getAllValues().get(0).getVerificationCount(),
                logs.getAllValues().get(1).getVerificationCount()));
        verify(mealPlanCustomerMapper, times(2)).markVerifiedIfPending(anyLong(), any(Date.class), eq("tester"));
        verify(customerOrderMapper, times(2)).incrementVerifiedCountAndAmount(10L, new BigDecimal("45.00"));
    }

    /**
     * 构建同一订单和餐次下不同份序的成功排餐结果。
     *
     * @param id 排餐客户记录ID
     * @param servingNo 配送份序号
     * @return 未核销的单份排餐记录
     */
    private MealPlanCustomer buildServing(Long id, int servingNo) {
        MealPlanCustomer serving = new MealPlanCustomer();
        serving.setId(id);
        serving.setMealPlanId(20L);
        serving.setCustomerId(30L);
        serving.setOrderId(10L);
        serving.setServingNo(servingNo);
        serving.setStatus(1);
        serving.setIsVerified(0);
        serving.setDeleted(false);
        return serving;
    }
}
