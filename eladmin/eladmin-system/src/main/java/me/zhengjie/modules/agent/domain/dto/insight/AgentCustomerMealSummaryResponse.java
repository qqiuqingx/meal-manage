package me.zhengjie.modules.agent.domain.dto.insight;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 客户餐数汇总响应 DTO
 * @author qqx
 * @date 2026-07-09
 */
@Getter
@Setter
public class AgentCustomerMealSummaryResponse {

    /** 客户是否存在 */
    private boolean present = true;

    /** 客户 ID */
    private Long customerId;

    /** 客户编号 */
    private String customerCode;

    /** 客户姓名 */
    private String customerName;

    /** 有效订单数 */
    private int activeOrderCount;

    /** 总订单数 */
    private int totalOrderCount;

    /** 剩余早餐数 */
    private int remainingBreakfast;

    /** 剩余午晚餐数 */
    private int remainingLunchDinner;

    /** 合计剩余餐数 */
    private int totalRemaining;

    /** 已核销早餐数 */
    private int verifiedBreakfast;

    /** 已核销午餐数 */
    private int verifiedLunch;

    /** 已核销晚餐数 */
    private int verifiedDinner;

    /** 合计已核销 */
    private int totalVerified;

    /** 订单明细 */
    private List<AgentCustomerOrderMealBalanceItem> orderItems;
}
