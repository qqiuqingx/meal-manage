package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/** 活跃客户的脱敏餐数余额明细，不包含电话、地址或金额。 */
@Data
public class AgentActiveCustomerBalanceItem {
    /** 内部关联客户 ID，不作为聊天展示字段。 */
    private Long customerId;
    /** 客户编号。 */
    private String customerCode;
    /** 脱敏客户姓名。 */
    private String customerNameMasked;
    /** 剩余早餐数。 */
    private int remainingBreakfast;
    /** 剩余午晚餐数。 */
    private int remainingLunchDinner;
    /** 两个餐数池的合计剩余数。 */
    private int remainingTotal;
}
