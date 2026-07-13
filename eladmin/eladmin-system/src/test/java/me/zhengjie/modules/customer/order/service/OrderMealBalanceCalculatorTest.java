package me.zhengjie.modules.customer.order.service;

import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealBalanceDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单餐数余额统一口径测试。
 */
class OrderMealBalanceCalculatorTest {

    /** 验证早餐与午晚餐独立计算，午餐和晚餐扣减同一池。 */
    @Test
    void shouldCalculateSeparateBreakfastAndLunchDinnerPools() {
        CustomerOrder order = new CustomerOrder();
        order.setBreakfastCount(3);
        order.setLunchDinnerCount(8);

        OrderMealBalanceDto result = OrderMealBalanceCalculator.calculate(order, 1, 2, 3);

        assertEquals(2, result.getRemainingBreakfast());
        assertEquals(3, result.getRemainingLunchDinner());
    }

    /** 验证核销数超过订单餐数时，剩余餐数不为负。 */
    @Test
    void shouldNeverReturnNegativeRemainingCount() {
        CustomerOrder order = new CustomerOrder();
        order.setBreakfastCount(1);
        order.setLunchDinnerCount(1);

        OrderMealBalanceDto result = OrderMealBalanceCalculator.calculate(order, 5, 1, 1);

        assertEquals(0, result.getRemainingBreakfast());
        assertEquals(0, result.getRemainingLunchDinner());
    }

    /** 验证进行中订单是否有效不依赖可能滞后的存储剩余餐数字段。 */
    @Test
    void shouldIdentifyActiveOrderByStatusAndStoredRemainingCount() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus(1);
        assertTrue(OrderMealBalanceCalculator.isActiveOrder(order));

        order.setStatus(2);
        assertFalse(OrderMealBalanceCalculator.isActiveOrder(order));
    }
}
