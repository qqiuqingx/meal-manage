package me.zhengjie.agent.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 固定业务话术必须只使用受控工具结果，不引入金额或写操作语义。 */
class BusinessAnswerComposerTest {
    private final BusinessAnswerComposer composer = new BusinessAnswerComposer();

    @Test
    void shouldComposeCustomerOrderAndHistoryMessagesFromControlledValues() {
        String overview = composer.customerOverview(Map.of("present", true, "customerCode", "B3303", "customerName", "张三",
            "activeOrderCount", 2, "mealBalance", Map.of("remainingBreakfast", 3, "remainingLunchDinner", 8)));
        String orders = composer.orderList(Map.of("total", 2L, "items", List.of()));
        String verification = composer.verificationList(Map.of("total", 3L, "items", List.of()));
        String refunds = composer.refundList(Map.of("total", 1L, "items", List.of()));

        assertTrue(overview.contains("2 笔进行中订单"));
        assertTrue(orders.contains("不含金额信息"));
        assertTrue(verification.contains("未删除核销记录"));
        assertTrue(refunds.contains("不包含退款金额"));
    }

    @Test
    void shouldComposeMealBalanceChangeFromHistoryTotals() {
        String message = composer.mealBalanceChange(Map.of(), Map.of("total", 3), Map.of("total", 1));

        assertTrue(message.contains("核销记录 3 条"));
        assertTrue(message.contains("退餐记录 1 条"));
    }
}
