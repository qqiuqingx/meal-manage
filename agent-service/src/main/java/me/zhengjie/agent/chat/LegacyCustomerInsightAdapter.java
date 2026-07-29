package me.zhengjie.agent.chat;

import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightMealRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightOrderRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightVerificationRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 旧诊断工具客户汇总接口的兼容适配器。
 *
 * <p>新业务查询走强类型 {@code BusinessQueryDataClient}；只有旧接口兜底允许在此处读取 Map 字段。</p>
 */
@Component
public class LegacyCustomerInsightAdapter {
    private final DiagnosisToolDataClient dataClient;

    /** 注入旧诊断工具客户端；该依赖不会进入新业务查询路径。 */
    public LegacyCustomerInsightAdapter(DiagnosisToolDataClient dataClient) {
        this.dataClient = dataClient;
    }

    /**
     * 调用与兼容意图对应的旧客户汇总接口。
     *
     * @param intent 已登记的客户查询意图
     * @param slots 当前受控槽位
     * @return 兼容响应类型、展示数据和固定话术
     */
    public LegacyInsight query(ChatIntent intent, DiagnosisSlots slots) {
        if (intent == ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY) return mealSummary(slots);
        if (intent == ChatIntent.CUSTOMER_VERIFICATION_QUERY) return verificationSummary(slots);
        if (intent == ChatIntent.CUSTOMER_ORDER_QUERY) return orderSummary(slots);
        throw new IllegalArgumentException("Unsupported legacy customer insight intent: " + intent);
    }

    /** 查询旧版客户餐数汇总并转换成兼容展示结果。 */
    private LegacyInsight mealSummary(DiagnosisSlots slots) {
        DiagnosisToolCustomerInsightMealRequest request = new DiagnosisToolCustomerInsightMealRequest();
        request.setCustomerId(slots.getCustomerId());
        request.setCustomerCode(slots.getCustomerCode());
        request.setMealType(slots.getMealType());
        Map<String, Object> result = dataClient.getCustomerMealSummary(request);
        return new LegacyInsight("CUSTOMER_MEAL_SUMMARY", result, buildMealBalanceMessage(result));
    }

    /** 查询旧版客户核销汇总并转换成兼容展示结果。 */
    private LegacyInsight verificationSummary(DiagnosisSlots slots) {
        DiagnosisToolCustomerInsightVerificationRequest request =
            new DiagnosisToolCustomerInsightVerificationRequest();
        request.setCustomerId(slots.getCustomerId());
        request.setCustomerCode(slots.getCustomerCode());
        request.setMealType(slots.getMealType());
        request.setRecentLimit(10);
        Map<String, Object> result = dataClient.getCustomerVerificationSummary(request);
        return new LegacyInsight("CUSTOMER_VERIFICATION_SUMMARY", result,
            buildVerificationMessage(result));
    }

    /** 查询旧版客户订单汇总并转换成兼容展示结果。 */
    private LegacyInsight orderSummary(DiagnosisSlots slots) {
        DiagnosisToolCustomerInsightOrderRequest request = new DiagnosisToolCustomerInsightOrderRequest();
        request.setCustomerId(slots.getCustomerId());
        request.setCustomerCode(slots.getCustomerCode());
        request.setOrderStatus(slots.getOrderStatus());
        Map<String, Object> result = dataClient.getCustomerOrderSummary(request);
        return new LegacyInsight("CUSTOMER_ORDER_SUMMARY", result, buildOrderMessage(result));
    }

    /** 生成旧版餐数汇总的固定话术，不把 Map 继续传给调用方读取。 */
    private String buildMealBalanceMessage(Map<String, Object> result) {
        if (result == null) return "查询异常，请稍后重试。";
        if (Boolean.FALSE.equals(result.get("present"))) return customerNotFound(result);
        String customerCode = stringValue(result.get("customerCode"));
        String customerName = stringValue(result.get("customerName"));
        int activeOrderCount = intValue(result.get("activeOrderCount"));
        int remainingBreakfast = intValue(result.get("remainingBreakfast"));
        int remainingLunchDinner = intValue(result.get("remainingLunchDinner"));
        int totalRemaining = intValue(result.get("totalRemaining"));
        int verifiedBreakfast = intValue(result.get("verifiedBreakfast"));
        int verifiedLunch = intValue(result.get("verifiedLunch"));
        int verifiedDinner = intValue(result.get("verifiedDinner"));
        int totalMealCount = totalRemaining + verifiedBreakfast + verifiedLunch + verifiedDinner;
        if (activeOrderCount == 0) {
            return String.format("%s 当前没有有效进行中订单，因此没有可继续核销的剩余餐数。历史订单和核销记录已列在下方供核对。",
                customerCode);
        }
        return String.format(
            "%s（%s）当前有效订单共 %d 笔，当前有效订单总餐数 %d 餐。剩余早餐 %d 餐，剩余午晚餐 %d 餐，合计剩余 %d 餐。已核销早餐 %d 餐，午餐 %d 餐，晚餐 %d 餐。数据按未删除核销日志实时汇总。",
            customerCode, customerName, activeOrderCount, totalMealCount, remainingBreakfast,
            remainingLunchDinner, totalRemaining, verifiedBreakfast, verifiedLunch, verifiedDinner);
    }

    /** 生成旧版核销汇总的固定话术。 */
    @SuppressWarnings("unchecked")
    private String buildVerificationMessage(Map<String, Object> result) {
        if (result == null) return "查询异常，请稍后重试。";
        if (Boolean.FALSE.equals(result.get("present"))) return customerNotFound(result);
        int recentCount = result.get("recentVerifications") instanceof List
            ? ((List<Map<String, Object>>) result.get("recentVerifications")).size() : 0;
        return String.format(
            "%s 累计已核销 %d 餐，其中早餐 %d 餐、午餐 %d 餐、晚餐 %d 餐。最近 %d 条核销记录已列在下方。",
            stringValue(result.get("customerCode")), intValue(result.get("totalVerified")),
            intValue(result.get("totalVerifiedBreakfast")), intValue(result.get("totalVerifiedLunch")),
            intValue(result.get("totalVerifiedDinner")), recentCount);
    }

    /** 生成旧版订单汇总的固定话术。 */
    @SuppressWarnings("unchecked")
    private String buildOrderMessage(Map<String, Object> result) {
        if (result == null) return "查询异常，请稍后重试。";
        if (Boolean.FALSE.equals(result.get("present"))) return customerNotFound(result);
        List<Map<String, Object>> orders = result.get("orders") instanceof List
            ? (List<Map<String, Object>>) result.get("orders") : List.of();
        long activeCount = orders.stream()
            .filter(order -> Integer.valueOf(1).equals(order.get("status"))).count();
        return String.format("%s 共有 %d 笔订单，其中进行中 %d 笔。订单明细已列在下方。",
            stringValue(result.get("customerCode")), orders.size(), activeCount);
    }

    /** 生成统一的客户未命中提示。 */
    private String customerNotFound(Map<String, Object> result) {
        return String.format("未找到客户编号 %s，请确认编号是否正确。",
            stringValue(result.get("customerCode")));
    }

    /** 将兼容 Map 数值安全转换为整数。 */
    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /** 将兼容 Map 文本安全转换为空值友好字符串。 */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 旧接口结果只允许在进入统一响应工厂前短暂保留。 */
    public record LegacyInsight(String responseType, Map<String, Object> result, String message) { }
}
