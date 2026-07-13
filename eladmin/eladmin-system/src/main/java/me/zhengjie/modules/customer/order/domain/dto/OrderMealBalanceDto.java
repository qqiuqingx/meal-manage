package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Value;

/**
 * 订单早餐池与午晚餐池的统一余额口径结果。
 */
@Value
public class OrderMealBalanceDto {

    /** 已核销早餐餐数。 */
    int verifiedBreakfast;

    /** 已核销午餐餐数。 */
    int verifiedLunch;

    /** 已核销晚餐餐数。 */
    int verifiedDinner;

    /** 剩余早餐餐数。 */
    int remainingBreakfast;

    /** 剩余午晚餐餐数，午餐和晚餐共用同一餐数池。 */
    int remainingLunchDinner;
}
