package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 客户订单餐数和金额余额重算结果。
 */
@Data
public class CustomerOrderBalanceRecalculateResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单ID。 */
    private Long orderId;

    /** 重算前已核销餐数。 */
    private Integer oldVerifiedCount;

    /** 重算后已核销餐数。 */
    private Integer newVerifiedCount;

    /** 重算前剩余餐数。 */
    private Integer oldRemainingCount;

    /** 重算后剩余餐数。 */
    private Integer newRemainingCount;

    /** 重算前已核销金额。 */
    private BigDecimal oldVerifiedAmount;

    /** 重算后已核销金额。 */
    private BigDecimal newVerifiedAmount;

    /** 重算前餐费余额。 */
    private BigDecimal oldMealBalance;

    /** 重算后餐费余额。 */
    private BigDecimal newMealBalance;

    /** 重算前订单状态。 */
    private Integer oldStatus;

    /** 重算后订单状态。 */
    private Integer newStatus;
}
