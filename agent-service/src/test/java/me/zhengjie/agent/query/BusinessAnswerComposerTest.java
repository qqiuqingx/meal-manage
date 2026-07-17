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
            "createTime", "2026-07-01T09:00:00", "firstPurchaseTime", "2026-07-02T10:30:00",
            "activeOrderCount", 2, "mealBalance", Map.of("remainingBreakfast", 3, "remainingLunchDinner", 8)));
        String orders = composer.orderList(Map.of("total", 2L, "items", List.of()));
        String verification = composer.verificationList(Map.of("total", 3L, "items", List.of()));
        String refunds = composer.refundList(Map.of("total", 1L, "items", List.of()));

        assertTrue(overview.contains("2 笔进行中订单"));
        assertTrue(overview.contains("客户档案创建于 2026-07-01 09:00:00"));
        assertTrue(overview.contains("首次购买于 2026-07-02 10:30:00"));
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

    /** 活跃客户统计和集合明细应使用业务语言说明可用餐数，不暴露内部口径代码。 */
    @Test
    void shouldComposeActiveCustomerBalanceMessagesInBusinessLanguage() {
        String count = composer.operationStatistics(Map.of("total", 15,
            "metricDefinitionId", "AGENT_ACTIVE_CUSTOMER_V1"), me.zhengjie.agent.query.domain.AgentQueryMetric.ACTIVE_CUSTOMER_COUNT);
        String details = composer.activeCustomerBalances(Map.of("total", 15, "items", List.of(Map.of())));

        assertTrue(count.contains("仍有可用餐数"));
        assertTrue(!count.contains("AGENT_ACTIVE_CUSTOMER_V1"));
        assertTrue(details.contains("各自的早餐、午晚餐和合计剩余餐数"));
    }
}
