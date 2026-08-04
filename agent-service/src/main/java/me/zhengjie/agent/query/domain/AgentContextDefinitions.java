package me.zhengjie.agent.query.domain;

/** 会话中可重新计算的受控业务集合定义。 */
public final class AgentContextDefinitions {
    /** 仍有可用餐数的活跃客户集合。 */
    public static final String ACTIVE_CUSTOMER = "AGENT_ACTIVE_CUSTOMER_V1";
    /** 当前授权数据范围内状态为进行中的订单集合。 */
    public static final String ACTIVE_ORDER = "AGENT_ACTIVE_ORDER_V1";

    private AgentContextDefinitions() { }
}
