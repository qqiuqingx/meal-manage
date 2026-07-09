package me.zhengjie.modules.agent.domain.dto.insight;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户订单餐数余额明细项
 * @author qqx
 * @date 2026-07-09
 */
@Getter
@Setter
public class AgentCustomerOrderMealBalanceItem {

    /** 订单 ID */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 订单状态：1=进行中 */
    private Integer status;

    /** 订单早餐总数 */
    private int breakfastCount;

    /** 订单午晚餐总数 */
    private int lunchDinnerCount;

    /** 已核销早餐 */
    private int verifiedBreakfast;

    /** 已核销午餐 */
    private int verifiedLunch;

    /** 已核销晚餐 */
    private int verifiedDinner;

    /** 剩余早餐 */
    private int remainingBreakfast;

    /** 剩余午晚餐 */
    private int remainingLunchDinner;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 订单开始日期 */
    private LocalDate startDate;

    /** 订单结束日期 */
    private LocalDate endDate;

    /** 订单创建时间 */
    private LocalDateTime createTime;
}
