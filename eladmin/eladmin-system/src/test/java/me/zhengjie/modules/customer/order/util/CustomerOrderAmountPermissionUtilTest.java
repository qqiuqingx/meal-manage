package me.zhengjie.modules.customer.order.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CustomerOrderAmountPermissionUtilTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldTreatMissingRequestContextAsNoAmountPermission() {
        RequestContextHolder.resetRequestAttributes();

        assertFalse(CustomerOrderAmountPermissionUtil.canViewAmount());
        assertFalse(CustomerOrderAmountPermissionUtil.canEditAmount());
    }

    @Test
    void shouldTreatMissingJwtAsNoAmountPermission() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertFalse(CustomerOrderAmountPermissionUtil.canViewAmount());
        assertFalse(CustomerOrderAmountPermissionUtil.canEditAmount());
    }
}
