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
    /** 顶层只读业务查询；具体领域、指标和工具由 BusinessQuestionAnalysis 与 QueryPlan 决定。 */
    BUSINESS_QUERY,

    // 客户信息查询类意图
    CUSTOMER_MEAL_BALANCE_QUERY,
    CUSTOMER_VERIFICATION_QUERY,
    CUSTOMER_ORDER_QUERY,
    CUSTOMER_REFUND_QUERY,
    CUSTOMER_PACKAGE_QUERY,
    MEAL_PLAN_QUERY,
    SCHEDULED_MENU_QUERY,
    MEAL_PLAN_UNVERIFIED_QUERY,
    MEAL_BALANCE_NO_PLAN_QUERY,
    MEAL_BALANCE_CHANGE_QUERY,
    DISH_INGREDIENT_QUERY,
    DISH_CANDIDATE_QUERY,
    BUSINESS_RULE_QUERY,
    /** 跨客户只读运营聚合查询，不返回客户明细。 */
    OPERATION_STATISTICS_QUERY
}
