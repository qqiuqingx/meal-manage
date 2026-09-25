package me.zhengjie.modules.customer.order.service.impl;

import cn.hutool.jwt.JWT;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderSaveDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.orderReplaceRule.mapper.CustomerOrderReplaceRuleMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.SpringBeanHolder;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceImplTest {

    @Mock
    private CustomerOrderMapper orderMapper;

    @Mock
    private MealPlanCustomerMapper mealPlanCustomerMapper;

    @Mock
    private CustomerProfileMapper profileMapper;

    @Mock
    private ParentPackageMapper parentPackageMapper;

    @Mock
    private SubPackageMapper subPackageMapper;

    @Mock
    private CustomerProfileService customerProfileService;

    @Mock
    private CustomerOrderReplaceRuleMapper replaceRuleMapper;

    @Mock
    private DishMapper dishMapper;

    @InjectMocks
    private CustomerOrderServiceImpl orderService;

    @Test
    void query_setsEstimatedRemainingCountFromCurrentRemainingMinusTodayUnverifiedPlans() {
        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setBreakfastCount(3);
        order.setLunchDinnerCount(7);
        order.setRemainingCount(6);

        OrderScheduledCountDto totalScheduledCount = new OrderScheduledCountDto();
        totalScheduledCount.setOrderId(10L);
        totalScheduledCount.setScheduledCount(5);

        OrderScheduledCountDto scheduledCount = new OrderScheduledCountDto();
        scheduledCount.setOrderId(10L);
        scheduledCount.setScheduledCount(2);

        when(orderMapper.findAll(any(CustomerOrderQueryCriteria.class), any(Page.class)))
                .thenReturn(Collections.singletonList(order));
        when(mealPlanCustomerMapper.countAllScheduledByOrderIds(eq(Collections.singletonList(10L))))
                .thenReturn(Collections.singletonList(totalScheduledCount));
        when(mealPlanCustomerMapper.countTodayUnverifiedScheduledByOrderIds(eq(Collections.singletonList(10L)), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(scheduledCount));

        PageResult<?> result = orderService.query(new CustomerOrderQueryCriteria(), 1, 10);

        @SuppressWarnings("unchecked")
        List<CustomerOrder> orders = (List<CustomerOrder>) result.getContent();
        assertEquals(1, orders.size());
        assertEquals(10, orders.get(0).getTotalCount());
        assertEquals(5, orders.get(0).getScheduledCount());
        assertEquals(4, orders.get(0).getEstimatedRemainingCount());

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(mealPlanCustomerMapper)
                .countTodayUnverifiedScheduledByOrderIds(eq(Collections.singletonList(10L)), dateCaptor.capture());
        assertNotNull(dateCaptor.getValue());
    }

    @Test
    void query_displaysImportedSourceTotalWithHistoricalVerification() {
        CustomerOrder order = new CustomerOrder();
        order.setId(11L);
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(7);
        order.setImportedVerifiedCount(3);
        order.setVerifiedCount(3);
        order.setRemainingCount(4);
        when(orderMapper.findAll(any(CustomerOrderQueryCriteria.class), any(Page.class)))
                .thenReturn(Collections.singletonList(order));

        setTestSecurityContext();
        try {
            PageResult<?> result = orderService.query(new CustomerOrderQueryCriteria(), 1, 10);

            @SuppressWarnings("unchecked")
            List<CustomerOrder> orders = (List<CustomerOrder>) result.getContent();
            assertEquals(Integer.valueOf(7), orders.get(0).getTotalCount());
            assertEquals(Integer.valueOf(3), orders.get(0).getVerifiedCount());
            assertEquals(Integer.valueOf(4), orders.get(0).getRemainingCount());
        } finally {
            clearTestSecurityContext();
        }
    }

    @Test
    void create_rejectsTrialConversionWhenLinkedOrderParentPackageIsNotTrialPackage() {
        CustomerProfile profile = new CustomerProfile();
        profile.setId(1L);
        profile.setCustomerCode("A1001");

        CustomerOrder linkedOrder = new CustomerOrder();
        linkedOrder.setId(20L);
        linkedOrder.setParentPackageId(30L);

        ParentPackage normalPackage = new ParentPackage();
        normalPackage.setId(30L);
        normalPackage.setPackageName("月子餐套餐");

        CustomerOrderSaveDto dto = new CustomerOrderSaveDto();
        dto.setCustomerId(1L);
        dto.setParentPackageId(40L);
        dto.setTotalAmount(BigDecimal.TEN);
        dto.setFinalAmount(BigDecimal.TEN);
        dto.setBreakfastCount(1);
        dto.setLunchDinnerCount(0);
        dto.setStartDate(LocalDate.of(2026, 5, 27));
        dto.setMealType("ALL");
        dto.setTrialConverted(true);
        dto.setTrialOrderId(20L);
        dto.setMainDishCount(1);
        dto.setSideDishCount(0);
        dto.setVegCount(1);
        dto.setSoupCount(0);

        when(profileMapper.selectById(1L)).thenReturn(profile);
        when(orderMapper.selectById(20L)).thenReturn(linkedOrder);
        when(parentPackageMapper.selectById(30L)).thenReturn(normalPackage);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.create(dto));

        assertEquals("关联订单必须是父套餐名称包含“试餐”的订单", ex.getMessage());
    }

    @Test
    void create_persistsCustomMenuImage() {
        CustomerProfile profile = buildProfile();
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setCustomMenuImage("/file/avatar/menu-001.jpg");

        when(profileMapper.selectById(1L)).thenReturn(profile);

        orderService.create(dto);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals("/file/avatar/menu-001.jpg", captor.getValue().getCustomMenuImage());
    }

    @Test
    void update_persistsCustomMenuImage() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(50L);
        existing.setCustomerId(1L);
        existing.setParentPackageId(40L);
        existing.setVerifiedCount(0);

        CustomerOrderSaveDto dto = buildValidDto();
        dto.setId(50L);
        dto.setCustomMenuImage("/file/avatar/menu-002.jpg");

        when(orderMapper.selectById(50L)).thenReturn(existing);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        orderService.update(dto);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertEquals("/file/avatar/menu-002.jpg", captor.getValue().getCustomMenuImage());
    }

    @Test
    void update_keepsImportedHistoricalVerificationWhenRequestOmitsVerifiedCount() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(51L);
        existing.setCustomerId(1L);
        existing.setParentPackageId(40L);
        existing.setStatus(1);
        existing.setBreakfastCount(0);
        existing.setLunchDinnerCount(7);
        existing.setImportedVerifiedCount(3);
        existing.setVerifiedCount(3);
        existing.setRemainingCount(4);
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setId(51L);
        dto.setBreakfastCount(0);
        dto.setLunchDinnerCount(7);
        dto.setMealType("LUNCH_DINNER");
        dto.setStatus(1);
        when(orderMapper.selectById(51L)).thenReturn(existing);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        setTestSecurityContext();
        try {
            orderService.update(dto);

            assertEquals(Integer.valueOf(3), existing.getImportedVerifiedCount());
            assertEquals(Integer.valueOf(3), existing.getVerifiedCount());
            assertEquals(Integer.valueOf(4), existing.getRemainingCount());
        } finally {
            clearTestSecurityContext();
        }
    }

    @Test
    void update_recordsFirstPauseDateAndKeepsItUntilResume() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(50L);
        existing.setCustomerId(1L);
        existing.setParentPackageId(40L);
        existing.setStatus(1);

        CustomerOrderSaveDto dto = buildValidDto();
        dto.setId(50L);
        dto.setStatus(4);
        when(orderMapper.selectById(50L)).thenReturn(existing);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        ApplicationContext context = mock(ApplicationContext.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(context.getBean(UserDetailsService.class)).thenReturn(userDetailsService);
        when(userDetailsService.loadUserByUsername("tester"))
                .thenReturn(new User("tester", "", Collections.emptyList()));
        new SpringBeanHolder().setApplicationContext(context);
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + JWT.create()
                .setKey("test-key".getBytes(StandardCharsets.UTF_8)).setPayload("sub", "tester").sign());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        try {
            orderService.update(dto);
            assertEquals(LocalDate.now(), existing.getPauseEffectiveDate());

            LocalDate firstPauseDate = existing.getPauseEffectiveDate();
            orderService.update(dto);
            assertEquals(firstPauseDate, existing.getPauseEffectiveDate());

            dto.setStatus(1);
            orderService.update(dto);
            assertEquals(null, existing.getPauseEffectiveDate());
        } finally {
            RequestContextHolder.resetRequestAttributes();
            new SpringBeanHolder().destroy();
        }
    }

    @Test
    void getDetail_returnsCustomMenuImageAndTrialOrderCode() {
        CustomerOrder order = new CustomerOrder();
        order.setId(50L);
        order.setCustomerId(1L);
        order.setParentPackageId(40L);
        order.setOrderCode("ORD20260531001");
        order.setBreakfastCount(1);
        order.setLunchDinnerCount(2);
        order.setTrialConverted(true);
        order.setTrialOrderId(60L);
        order.setCustomMenuImage("/file/avatar/menu-003.jpg");

        CustomerOrder trialOrder = new CustomerOrder();
        trialOrder.setId(60L);
        trialOrder.setOrderCode("ORD20260523001");

        when(orderMapper.selectById(50L)).thenReturn(order);
        when(orderMapper.selectById(60L)).thenReturn(trialOrder);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());
        when(replaceRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertEquals("/file/avatar/menu-003.jpg", orderService.getDetail(50L).getCustomMenuImage());
        assertEquals("ORD20260523001", orderService.getDetail(50L).getTrialOrderCode());
    }

    @Test
    void create_allowsTrialConversionWhenLinkedOrderParentPackageIsTrialPackage() {
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setTrialConverted(true);
        dto.setTrialOrderId(60L);

        CustomerOrder trialOrder = new CustomerOrder();
        trialOrder.setId(60L);
        trialOrder.setParentPackageId(70L);

        ParentPackage trialPackage = new ParentPackage();
        trialPackage.setId(70L);
        trialPackage.setPackageName("轻食试餐套餐");

        when(profileMapper.selectById(1L)).thenReturn(buildProfile());
        when(orderMapper.selectById(60L)).thenReturn(trialOrder);
        when(parentPackageMapper.selectById(70L)).thenReturn(trialPackage);

        orderService.create(dto);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getTrialConverted());
        assertEquals(60L, captor.getValue().getTrialOrderId());
    }

    @Test
    void create_clearsTrialOrderWhenTrialConvertedIsFalse() {
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setTrialConverted(false);
        dto.setTrialOrderId(60L);

        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        orderService.create(dto);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertFalse(captor.getValue().getTrialConverted());
        assertEquals(null, captor.getValue().getTrialOrderId());
        verify(orderMapper, never()).selectById(60L);
    }

    @Test
    void create_rejectsTrialConversionWithoutLinkedOrder() {
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setTrialConverted(true);
        dto.setTrialOrderId(null);

        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.create(dto));

        assertEquals("请选择关联试餐订单", ex.getMessage());
    }

    @Test
    void update_rejectsTrialConversionLinkedToCurrentOrder() {
        CustomerOrder existing = new CustomerOrder();
        existing.setId(50L);
        existing.setCustomerId(1L);
        existing.setParentPackageId(40L);
        existing.setVerifiedCount(0);

        CustomerOrderSaveDto dto = buildValidDto();
        dto.setId(50L);
        dto.setTrialConverted(true);
        dto.setTrialOrderId(50L);

        when(orderMapper.selectById(50L)).thenReturn(existing);
        when(profileMapper.selectById(1L)).thenReturn(buildProfile());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.update(dto));

        assertEquals("关联试餐订单不能选择当前订单", ex.getMessage());
    }

    @Test
    void create_rejectsMissingLinkedTrialOrder() {
        CustomerOrderSaveDto dto = buildValidDto();
        dto.setTrialConverted(true);
        dto.setTrialOrderId(60L);

        when(profileMapper.selectById(1L)).thenReturn(buildProfile());
        when(orderMapper.selectById(60L)).thenReturn(null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.create(dto));

        assertEquals("关联试餐订单不存在", ex.getMessage());
    }

    private CustomerProfile buildProfile() {
        CustomerProfile profile = new CustomerProfile();
        profile.setId(1L);
        profile.setCustomerCode("A1001");
        profile.setCustomerName("张三");
        return profile;
    }

    private CustomerOrderSaveDto buildValidDto() {
        CustomerOrderSaveDto dto = new CustomerOrderSaveDto();
        dto.setCustomerId(1L);
        dto.setParentPackageId(40L);
        dto.setTotalAmount(BigDecimal.TEN);
        dto.setFinalAmount(BigDecimal.TEN);
        dto.setBreakfastCount(1);
        dto.setLunchDinnerCount(2);
        dto.setStartDate(LocalDate.of(2026, 5, 27));
        dto.setMealType("ALL");
        dto.setTrialConverted(false);
        dto.setMainDishCount(1);
        dto.setSideDishCount(0);
        dto.setVegCount(1);
        dto.setSoupCount(0);
        return dto;
    }

    /**
     * 为真实订单查询与编辑路径准备登录上下文。
     */
    private void setTestSecurityContext() {
        ApplicationContext context = mock(ApplicationContext.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(context.getBean(UserDetailsService.class)).thenReturn(userDetailsService);
        when(userDetailsService.loadUserByUsername("tester"))
                .thenReturn(new User("tester", "", Collections.emptyList()));
        new SpringBeanHolder().setApplicationContext(context);
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + JWT.create()
                .setKey("test-key".getBytes(StandardCharsets.UTF_8)).setPayload("sub", "tester").sign());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
    }

    /**
     * 清理当前测试建立的登录上下文。
     */
    private void clearTestSecurityContext() {
        RequestContextHolder.resetRequestAttributes();
        new SpringBeanHolder().destroy();
    }
}
