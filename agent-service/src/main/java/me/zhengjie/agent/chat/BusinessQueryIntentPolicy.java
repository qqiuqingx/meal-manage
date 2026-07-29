package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 兼容业务查询意图的受控判定策略。
 *
 * <p>所有兼容映射只接受提取器给出的枚举名；禁止把用户原文转换成工具名或任意能力标识。</p>
 */
@Component
public class BusinessQueryIntentPolicy {

    /**
     * 判断是否为已登记的客户信息查询意图。
     *
     * @param intent 受控聊天意图
     * @return 客户信息查询返回 true
     */
    public boolean isCustomerInsightIntent(ChatIntent intent) {
        return intent == ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY
            || intent == ChatIntent.CUSTOMER_VERIFICATION_QUERY
            || intent == ChatIntent.CUSTOMER_ORDER_QUERY
            || intent == ChatIntent.CUSTOMER_REFUND_QUERY
            || intent == ChatIntent.CUSTOMER_PACKAGE_QUERY
            || intent == ChatIntent.MEAL_BALANCE_CHANGE_QUERY;
    }

    /**
     * 将顶层业务查询映射到已登记兼容意图。
     *
     * @param extraction 当前受控提取结果
     * @return 可执行兼容意图；未知或顶层控制意图返回 null
     */
    public ChatIntent compatibilityIntent(ChatExtractionResult extraction) {
        if (extraction == null || !hasText(extraction.getRuleIntent())) return null;
        try {
            ChatIntent intent = ChatIntent.valueOf(extraction.getRuleIntent());
            return intent == ChatIntent.BUSINESS_QUERY || intent == ChatIntent.DIAGNOSE
                || intent == ChatIntent.FOLLOW_UP || intent == ChatIntent.RETRY
                || intent == ChatIntent.RESET || intent == ChatIntent.OUT_OF_SCOPE ? null : intent;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 判断问题是否命中本期明确禁止返回的金额范围。
     *
     * @param message 客服问题
     * @return 命中金额类问题返回 true
     */
    public boolean isAmountQuery(String message) {
        if (message == null) return false;
        return message.contains("订单金额") || message.contains("退款金额") || message.contains("优惠金额")
            || message.contains("已收金额") || message.contains("单价")
            || message.contains("多少钱") || message.contains("价格");
    }

    /**
     * 返回客户信息查询尚缺的客户槽位。
     *
     * @param slots 当前会话槽位
     * @return 无客户或订单标识时返回 CUSTOMER
     */
    public List<MissingSlot> missingSlotsForInsight(DiagnosisSlots slots) {
        if (slots == null || slots.getCustomerId() == null && !hasText(slots.getCustomerCode())
            && !hasText(slots.getCustomerName()) && slots.getOrderId() == null
            && !hasText(slots.getOrderCode())) {
            return List.of(MissingSlot.CUSTOMER);
        }
        return List.of();
    }

    /** 判断受控槽位文本是否包含有效内容。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
