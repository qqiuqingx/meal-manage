package me.zhengjie.agent.chat;

import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyCustomerInsightAdapterTest {

    @Test
    void confinesLegacyCustomerSummaryMapsToCompatibilityAdapter() {
        DiagnosisToolDataClient client = (DiagnosisToolDataClient) Proxy.newProxyInstance(
            DiagnosisToolDataClient.class.getClassLoader(),
            new Class<?>[]{DiagnosisToolDataClient.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getCustomerMealSummary" -> Map.of(
                    "present", true, "customerCode", "C1001", "customerName", "张三",
                    "activeOrderCount", 1, "remainingBreakfast", 2, "remainingLunchDinner", 3,
                    "totalRemaining", 5, "verifiedBreakfast", 1, "verifiedLunch", 2, "verifiedDinner", 1);
                case "getCustomerVerificationSummary" -> Map.of(
                    "present", true, "customerCode", "C1001", "totalVerified", 4,
                    "totalVerifiedBreakfast", 1, "totalVerifiedLunch", 2,
                    "totalVerifiedDinner", 1, "recentVerifications", List.of(Map.of("id", 1)));
                case "getCustomerOrderSummary" -> Map.of(
                    "present", true, "customerCode", "C1001",
                    "orders", List.of(Map.of("status", 1), Map.of("status", 2)));
                default -> throw new UnsupportedOperationException(method.getName());
            });
        LegacyCustomerInsightAdapter adapter = new LegacyCustomerInsightAdapter(client);
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode("C1001");

        LegacyCustomerInsightAdapter.LegacyInsight meal =
            adapter.query(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, slots);
        LegacyCustomerInsightAdapter.LegacyInsight verification =
            adapter.query(ChatIntent.CUSTOMER_VERIFICATION_QUERY, slots);
        LegacyCustomerInsightAdapter.LegacyInsight orders =
            adapter.query(ChatIntent.CUSTOMER_ORDER_QUERY, slots);

        assertEquals("CUSTOMER_MEAL_SUMMARY", meal.responseType());
        assertTrue(meal.message().contains("合计剩余 5 餐"));
        assertTrue(verification.message().contains("累计已核销 4 餐"));
        assertTrue(orders.message().contains("共有 2 笔订单，其中进行中 1 笔"));
    }
}
