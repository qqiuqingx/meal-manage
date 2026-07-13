package me.zhengjie.agent.query.domain;

/**
 * Agent 受控只读查询所属的业务领域。
 */
public enum AgentQueryDomain {
    CUSTOMER,
    ORDER,
    MEAL_PLAN,
    VERIFICATION,
    REFUND,
    PACKAGE,
    DISH,
    BUSINESS_RULE,
    /** 跨客户、聚合后的运营统计，不返回未授权客户明细。 */
    OPERATION_STATISTICS,
    /** 由指标目录约束的自然语言汇总报表。 */
    NATURAL_LANGUAGE_REPORT
}
