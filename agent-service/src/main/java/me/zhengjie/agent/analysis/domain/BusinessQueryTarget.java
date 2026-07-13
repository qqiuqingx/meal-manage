package me.zhengjie.agent.analysis.domain;

/** 受控业务查询目标，不映射为模型可自由选择的工具名。 */
public enum BusinessQueryTarget {
    SCHEDULED_MENU,
    MEAL_PLAN_ALLERGY_ANALYSIS,
    MEAL_PLAN_DIAGNOSIS,
    CUSTOMER_MEAL_PLAN,
    DISH_CANDIDATES,
    CUSTOMER,
    ORDER,
    VERIFICATION,
    REFUND,
    PACKAGE,
    BUSINESS_RULE,
    OPERATION_STATISTICS
}
