package me.zhengjie.agent.query.domain;

/**
 * 指标目录允许的聚合维度。枚举值是内部协议，不接受模型传入任意字段名。
 */
public enum AgentQueryDimension {
    RECORD_DATE,
    MEAL_TYPE,
    PACKAGE,
    CUSTOMER_SOURCE,
    ORDER_STATUS,
    MEAL_PLAN_STATUS,
    VERIFICATION_STATUS
}
