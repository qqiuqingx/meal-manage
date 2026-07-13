package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/**
 * Agent 可展示的订单双餐数池余额，不包含金额及单价字段。
 */
@Data
public class AgentOrderMealBalanceDto {

    /** 早餐总餐数。 */
    private int breakfastCount;
    /** 午晚餐总餐数。 */
    private int lunchDinnerCount;
    /** 已核销早餐数。 */
    private int verifiedBreakfast;
    /** 已核销午餐数。 */
    private int verifiedLunch;
    /** 已核销晚餐数。 */
    private int verifiedDinner;
    /** 剩余早餐数。 */
    private int remainingBreakfast;
    /** 剩余午晚餐数。 */
    private int remainingLunchDinner;
}
