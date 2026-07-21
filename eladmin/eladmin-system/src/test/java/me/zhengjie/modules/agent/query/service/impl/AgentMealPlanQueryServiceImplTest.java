package me.zhengjie.modules.agent.query.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanQueryRequest;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerItemMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanManualReplaceMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private CustomerProfileMapper customerProfileMapper;
    @InjectMocks private AgentMealPlanQueryServiceImpl service;

    @AfterEach
    void clearDataScope() {
        AgentCustomerDataScopeContext.clear();
    }

    @Test
    void shouldNotReturnMealPlanRecordThatBelongsToAnotherCustomer() {
        MealPlanCustomer customerPlan = new MealPlanCustomer(); customerPlan.setId(8001L); customerPlan.setCustomerId(2002L);
        when(mealPlanCustomerMapper.selectById(8001L)).thenReturn(customerPlan);
        AgentMealPlanQueryRequest request = new AgentMealPlanQueryRequest(); request.setCustomerId(1001L); request.setCustomerMealPlanId(8001L);

        assertEquals(0, service.query(request).getTotal());

        verify(mealPlanCustomerMapper).selectById(8001L);
        verifyNoInteractions(mealPlanMapper, mealPlanCustomerItemMapper, mealPlanManualReplaceMapper, customerMealScheduleAdditionMapper);
    }

    @Test
    void shouldRejectRangeQueryWhenCustomerDataScopeIsUnbound() {
        AgentCustomerDataScopeContext.clear();
        AgentMealPlanQueryRequest request = new AgentMealPlanQueryRequest();
        request.setRecordDate("2026-07-13"); request.setMealType("LUNCH"); request.setPage(1); request.setSize(50);

        assertEquals(0, service.query(request).getTotal());
        verifyNoInteractions(mealPlanMapper, mealPlanCustomerMapper, mealPlanCustomerItemMapper, customerMealScheduleAdditionMapper);
    }

    /** 全部过滤条件为空时也必须按数据范围和分页查询历史，不能一次性加载全表。 */
    @Test
    void shouldPageUnboundedHistoryWhenAllFiltersAreEmpty() {
        AgentCustomerDataScopeContext.bind(null);
        AgentMealPlanQueryRequest request = new AgentMealPlanQueryRequest();
        request.setPage(1); request.setSize(1);
        MealPlanCustomer customer = new MealPlanCustomer();
        customer.setId(8001L); customer.setMealPlanId(9001L); customer.setCustomerId(68L);
        Page<MealPlanCustomer> page = new Page<>(1, 1);
        page.setRecords(java.util.List.of(customer)); page.setTotal(3L);
        when(mealPlanCustomerMapper.selectAgentPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Page.class)))
            .thenReturn(page);
        MealPlan plan = new MealPlan(); plan.setId(9001L); plan.setRecordDate(java.time.LocalDate.parse("2026-05-28")); plan.setMealType("DINNER");
        when(mealPlanMapper.selectByIds(java.util.List.of(9001L))).thenReturn(java.util.List.of(plan));

        me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto<?> result = service.query(request);

        assertEquals(3L, result.getTotal());
        assertEquals(1, result.getItems().size());
        assertEquals(true, result.isTruncated());
        verify(mealPlanCustomerMapper).selectAgentPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Page.class));
    }
}
