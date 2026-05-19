package me.zhengjie.modules.agent.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanQueryCriteria;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDiagnosisContextServiceImplTest {

    @Mock
    private CustomerProfileService customerProfileService;

    @Mock
    private CustomerOrderService customerOrderService;

    @Mock
    private MealPlanService mealPlanService;

    @InjectMocks
    private AgentDiagnosisContextServiceImpl service;

    @Test
    void shouldAssembleDiagnosisContext() {
        MealPlanDiagnosisContextRequest request = new MealPlanDiagnosisContextRequest();
        request.setCustomerId(1001L);
        request.setCustomerCode("C1001");
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        CustomerProfileDetailDto profile = new CustomerProfileDetailDto();
        profile.setId(1001L);
        profile.setCustomerCode("C1001");
        profile.setCustomerName("张三");
        when(customerProfileService.getDetail(1001L)).thenReturn(profile);

        CustomerOrderDetailDto order = new CustomerOrderDetailDto();
        order.setId(2001L);
        order.setCustomerId(1001L);
        order.setCustomerName("张三");
        PageResult<CustomerOrderDetailDto> orderPage = new PageResult<>(Collections.singletonList(order), 1L);
        when(customerOrderService.getOrdersByCustomerId(1001L, 1, 100)).thenReturn((PageResult) orderPage);

        MealPlan plan = new MealPlan();
        plan.setId(3001L);
        PageResult<MealPlan> mealPlanPage = new PageResult<>(Collections.singletonList(plan), 1L);
        when(mealPlanService.queryAll(any(MealPlanQueryCriteria.class))).thenReturn(mealPlanPage);

        MealPlanDetailVO detail = new MealPlanDetailVO();
        detail.setTotalCustomers(1);
        when(mealPlanService.queryMealPlanDetail(3001L)).thenReturn(detail);

        MealPackageStatDto stat = new MealPackageStatDto();
        stat.setPackageCode("PKG001");
        stat.setPackageName("月子餐");
        stat.setMealCount(12);
        when(mealPlanService.statByDate("2026-05-17")).thenReturn(Collections.singletonList(stat));

        MealPlanDiagnosisContextDto context = service.buildContext(request);

        assertNotNull(context);
        assertEquals(1001L, context.getCustomerId());
        assertEquals("C1001", context.getCustomerCode());
        assertEquals("张三", context.getCustomerName());
        assertEquals("2026-05-17", context.getRecordDate());
        assertEquals("LUNCH", context.getMealType());
        assertNotNull(context.getCustomerProfile());
        assertEquals(1, context.getOrders().size());
        assertNotNull(context.getMealPlan());
        assertEquals(1, context.getMealPlan().getTotalCustomers());
        assertEquals(1, context.getCandidateDishStats().size());

        ArgumentCaptor<MealPlanQueryCriteria> criteriaCaptor = ArgumentCaptor.forClass(MealPlanQueryCriteria.class);
        verify(mealPlanService).queryAll(criteriaCaptor.capture());
        assertEquals("2026-05-17", criteriaCaptor.getValue().getRecordDate());
        assertEquals("LUNCH", criteriaCaptor.getValue().getMealType());
        assertEquals(1, criteriaCaptor.getValue().getPage().intValue());
        assertEquals(1, criteriaCaptor.getValue().getSize().intValue());
    }

    @Test
    void shouldHandleMissingProfileAndOrdersGracefully() {
        MealPlanDiagnosisContextRequest request = new MealPlanDiagnosisContextRequest();
        request.setCustomerId(1002L);
        request.setRecordDate("2026-05-18");
        request.setMealType("DINNER");

        when(customerProfileService.getDetail(1002L)).thenReturn(null);
        when(customerOrderService.getOrdersByCustomerId(1002L, 1, 100)).thenReturn(new PageResult<>(Collections.emptyList(), 0L));
        when(mealPlanService.queryAll(any(MealPlanQueryCriteria.class))).thenReturn(new PageResult<>(Collections.emptyList(), 0L));
        when(mealPlanService.statByDate(anyString())).thenReturn(Collections.emptyList());

        MealPlanDiagnosisContextDto context = service.buildContext(request);

        assertNotNull(context);
        assertEquals(1002L, context.getCustomerId());
        assertEquals(0, context.getOrders().size());
        assertNotNull(context.getCandidateDishStats());
        assertEquals(0, context.getCandidateDishStats().size());
    }

    @Test
    void shouldResolveCustomerByCodeWhenCustomerIdMissing() {
        MealPlanDiagnosisContextRequest request = new MealPlanDiagnosisContextRequest();
        request.setCustomerCode("C1003");
        request.setRecordDate("2026-05-19");
        request.setMealType("LUNCH");

        CustomerProfile profileEntity = new CustomerProfile();
        profileEntity.setId(1003L);
        profileEntity.setCustomerCode("C1003");
        PageResult<CustomerProfile> profilePage = new PageResult<>(Collections.singletonList(profileEntity), 1L);
        when(customerProfileService.queryAll(any(CustomerProfileQueryCriteria.class), any(Page.class))).thenReturn(profilePage);

        CustomerProfileDetailDto profileDetail = new CustomerProfileDetailDto();
        profileDetail.setId(1003L);
        profileDetail.setCustomerCode("C1003");
        profileDetail.setCustomerName("李四");
        when(customerProfileService.getDetail(1003L)).thenReturn(profileDetail);

        CustomerOrderDetailDto order = new CustomerOrderDetailDto();
        order.setId(2002L);
        order.setCustomerId(1003L);
        PageResult<CustomerOrderDetailDto> orderPage = new PageResult<>(Collections.singletonList(order), 1L);
        when(customerOrderService.getOrdersByCustomerId(1003L, 1, 100)).thenReturn((PageResult) orderPage);

        when(mealPlanService.queryAll(any(MealPlanQueryCriteria.class))).thenReturn(new PageResult<>(Collections.emptyList(), 0L));
        when(mealPlanService.statByDate(anyString())).thenReturn(Collections.emptyList());

        MealPlanDiagnosisContextDto context = service.buildContext(request);

        assertNotNull(context);
        assertEquals(1003L, context.getCustomerId());
        assertEquals("李四", context.getCustomerName());
        assertEquals(1, context.getOrders().size());
    }

    @Test
    void shouldResolveCustomerProfileById() {
        CustomerProfileDetailDto profile = new CustomerProfileDetailDto();
        profile.setId(1001L);
        profile.setCustomerName("张三");
        when(customerProfileService.getDetail(1001L)).thenReturn(profile);

        CustomerProfileDetailDto result = service.resolveCustomerProfile(1001L, null);

        assertNotNull(result);
        assertEquals(1001L, result.getId());
        assertEquals("张三", result.getCustomerName());
    }

    @Test
    void shouldResolveOrdersByCustomerCodeWithNormalizedPaging() {
        CustomerProfile profileEntity = new CustomerProfile();
        profileEntity.setId(1003L);
        profileEntity.setCustomerCode("C1003");
        when(customerProfileService.queryAll(any(CustomerProfileQueryCriteria.class), any(Page.class)))
                .thenReturn(new PageResult<>(Collections.singletonList(profileEntity), 1L));

        CustomerProfileDetailDto detail = new CustomerProfileDetailDto();
        detail.setId(1003L);
        when(customerProfileService.getDetail(1003L)).thenReturn(detail);

        CustomerOrderDetailDto order = new CustomerOrderDetailDto();
        order.setId(2001L);
        order.setCustomerId(1003L);
        when(customerOrderService.getOrdersByCustomerId(1003L, 1, 100))
                .thenReturn((PageResult) new PageResult<>(Collections.singletonList(order), 1L));

        java.util.List<CustomerOrderDetailDto> result = service.resolveOrders(null, "C1003", -1, 500);

        assertEquals(1, result.size());
        assertEquals(2001L, result.get(0).getId());
        verify(customerOrderService).getOrdersByCustomerId(1003L, 1, 100);
    }

    @Test
    void shouldResolveMealPlanDetail() {
        MealPlan plan = new MealPlan();
        plan.setId(3001L);
        when(mealPlanService.queryAll(any(MealPlanQueryCriteria.class)))
                .thenReturn(new PageResult<>(Collections.singletonList(plan), 1L));

        MealPlanDetailVO detail = new MealPlanDetailVO();
        detail.setTotalCustomers(3);
        when(mealPlanService.queryMealPlanDetail(3001L)).thenReturn(detail);

        MealPlanDetailVO result = service.resolveMealPlan("2026-05-17", "LUNCH");

        assertNotNull(result);
        assertEquals(3, result.getTotalCustomers());
    }

    @Test
    void shouldResolveCandidateDishStats() {
        MealPackageStatDto stat = new MealPackageStatDto();
        stat.setPackageCode("PKG001");
        when(mealPlanService.statByDate("2026-05-17")).thenReturn(Collections.singletonList(stat));

        java.util.List<MealPackageStatDto> result = service.resolveCandidateDishStats("2026-05-17");

        assertEquals(1, result.size());
        assertEquals("PKG001", result.get(0).getPackageCode());
    }
}
