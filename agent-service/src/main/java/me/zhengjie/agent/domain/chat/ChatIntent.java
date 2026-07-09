package me.zhengjie.agent.domain.chat;

/**
 * 聊天意图。
 */
public enum ChatIntent {
    DIAGNOSE,
    FOLLOW_UP,
    RETRY,
    RESET,
    OUT_OF_SCOPE,

    // 客户信息查询类意图
    CUSTOMER_MEAL_BALANCE_QUERY,
    CUSTOMER_VERIFICATION_QUERY,
    CUSTOMER_ORDER_QUERY
}
