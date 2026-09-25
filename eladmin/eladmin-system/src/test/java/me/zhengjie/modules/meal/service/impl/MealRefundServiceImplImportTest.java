package me.zhengjie.modules.meal.service.impl;

import cn.hutool.jwt.JWT;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.meal.domain.MealRefundLog;
import me.zhengjie.modules.meal.domain.dto.MealRefundDto;
import me.zhengjie.modules.meal.mapper.MealRefundLogMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import me.zhengjie.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 批量导入历史核销基数参与退餐计算的回归测试。
 */
@ExtendWith(MockitoExtension.class)
class MealRefundServiceImplImportTest {

    @Mock private CustomerOrderMapper orderMapper;
    @Mock private MealRefundLogMapper refundLogMapper;
    @Mock private MealVerificationLogMapper verificationLogMapper;

    @Test
    void refundShouldExcludeImportedHistoricalMeals() {
        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setCustomerId(20L);
        order.setStatus(1);
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(7);
        order.setImportedVerifiedCount(3);
        order.setLunchDinnerPrice(BigDecimal.ONE);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(verificationLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(orderMapper.updateStatusToRefunded(10L)).thenReturn(1);
        MealRefundDto dto = new MealRefundDto();
        dto.setOrderId(10L);
        dto.setRefundReason("测试退餐");
        MealRefundServiceImpl service = new MealRefundServiceImpl(orderMapper, refundLogMapper, verificationLogMapper);
        setTestSecurityContext();
        try {
            MealRefundLog result = service.refund(dto);

            assertEquals(Integer.valueOf(4), result.getRefundLunchDinnerCount());
            assertEquals(Integer.valueOf(3), result.getVerifiedLunchDinnerCount());
            assertEquals(new BigDecimal("4"), result.getRefundAmount());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /**
     * 为退餐服务的真实用户获取路径准备测试登录态。
     */
    private void setTestSecurityContext() {
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + JWT.create()
                .setKey("test-key".getBytes(StandardCharsets.UTF_8)).setPayload("sub", "tester").sign());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
