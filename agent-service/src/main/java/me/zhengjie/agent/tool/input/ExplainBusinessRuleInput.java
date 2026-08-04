package me.zhengjie.agent.tool.input;

/** 查询版本化业务规则的强类型输入。 */
public class ExplainBusinessRuleInput {
    private BusinessRuleTopic topic;

    public BusinessRuleTopic getTopic() { return topic; }
    public void setTopic(BusinessRuleTopic value) { topic = value; }

    /** 规则目录中已登记的主题，不允许任意文档路径。 */
    public enum BusinessRuleTopic {
        MEAL_BALANCE,
        ORDER_EFFECTIVE,
        MEAL_PLAN_MATCH,
        DIETARY_FILTER,
        VERIFICATION_REFUND_EFFECT
    }
}
