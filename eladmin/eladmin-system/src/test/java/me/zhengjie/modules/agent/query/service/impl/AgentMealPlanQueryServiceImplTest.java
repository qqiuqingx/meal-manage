package me.zhengjie.modules.agent.query.service.impl;

import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanQueryRequest;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerItemMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanManualReplaceMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 客户排餐记录详情必须校验客户归属，禁止通过记录 ID 猜测越权。 */
@ExtendWith(MockitoExtension.class)
class AgentMealPlanQueryServiceImplTest {
    @Mock private MealPlanMapper mealPlanMapper;
    @Mock private MealPlanCustomerMapper mealPlanCustomerMapper;
    @Mock private MealPlanCustomerItemMapper mealPlanCustomerItemMapper;
    @Mock private MealPlanManualReplaceMapper mealPlanManualReplaceMapper;
    @Mock private CustomerMealScheduleAdditionMapper customerMealScheduleAdditionMapper;
    @InjectMocks private AgentMealPlanQueryServiceImpl service;

    @Test
    void shouldNotReturnMealPlanRecordThatBelongsToAnotherCustomer() {
        MealPlanCustomer customerPlan = new MealPlanCustomer(); customerPlan.setId(8001L); customerPlan.setCustomerId(2002L);
        when(mealPlanCustomerMapper.selectById(8001L)).thenReturn(customerPlan);
        AgentMealPlanQueryRequest request = new AgentMealPlanQueryRequest(); request.setCustomerId(1001L); request.setCustomerMealPlanId(8001L);

        assertEquals(0, service.query(request).getTotal());

        verify(mealPlanCustomerMapper).selectById(8001L);
        verifyNoInteractions(mealPlanMapper, mealPlanCustomerItemMapper, mealPlanManualReplaceMapper, customerMealScheduleAdditionMapper);
    }
}
