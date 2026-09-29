package me.zhengjie.modules.customer.profile.rest;

import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarSaveResult;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsRowDto;
import me.zhengjie.modules.customer.profile.service.CustomerMealStatsService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerMealStatsControllerTest {

    @Mock
    private CustomerMealStatsService customerMealStatsService;

    @InjectMocks
    private CustomerProfileController controller;

    @Test
    void shouldRouteCalendarReadAndSaveByOrderId() {
        CustomerOrderMealCalendarDto calendar = new CustomerOrderMealCalendarDto();
        calendar.setOrderId(10L);
        when(customerMealStatsService.getOrderCalendar(10L, "2026-10")).thenReturn(calendar);
        CustomerOrderMealCalendarSaveDto request = new CustomerOrderMealCalendarSaveDto();
        CustomerOrderMealCalendarSaveResult saved = new CustomerOrderMealCalendarSaveResult();
        saved.setOrderId(10L);
        when(customerMealStatsService.saveOrderCalendar(10L, request)).thenReturn(saved);

        ResponseEntity<CustomerOrderMealCalendarDto> getResponse = controller.getOrderMealCalendar(10L, "2026-10");
        ResponseEntity<CustomerOrderMealCalendarSaveResult> putResponse = controller.saveOrderMealCalendar(10L, request);

        assertEquals(calendar, getResponse.getBody());
        assertEquals(saved, putResponse.getBody());
        verify(customerMealStatsService).getOrderCalendar(10L, "2026-10");
        verify(customerMealStatsService).saveOrderCalendar(10L, request);
    }

    @Test
    void shouldRouteOrderListQueryToTheOrderStatsService() {
        CustomerMealStatsQueryCriteria criteria = new CustomerMealStatsQueryCriteria();
        PageResult<CustomerMealStatsRowDto> result = new PageResult<>(Collections.emptyList(), 0L);
        when(customerMealStatsService.queryMealStats(criteria, 1, 20)).thenReturn(result);

        ResponseEntity<PageResult<CustomerMealStatsRowDto>> response = controller.queryMealStats(criteria, 1, 20);

        assertEquals(result, response.getBody());
        verify(customerMealStatsService).queryMealStats(criteria, 1, 20);
    }

    @Test
    void shouldKeepCalendarRoutesAndPermissionsOnTheCustomerProfileApi() throws Exception {
        Method getMethod = CustomerProfileController.class.getMethod("getOrderMealCalendar", Long.class, String.class);
        Method putMethod = CustomerProfileController.class.getMethod(
                "saveOrderMealCalendar", Long.class, CustomerOrderMealCalendarSaveDto.class);

        assertEquals("/mealStats/orders/{orderId}/calendar", getMethod.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/mealStats/orders/{orderId}/calendar", putMethod.getAnnotation(PutMapping.class).value()[0]);
        assertEquals("@el.check('customerProfile:list')", getMethod.getAnnotation(PreAuthorize.class).value());
        assertEquals("@el.check('customerProfile:edit')", putMethod.getAnnotation(PreAuthorize.class).value());
    }
}
