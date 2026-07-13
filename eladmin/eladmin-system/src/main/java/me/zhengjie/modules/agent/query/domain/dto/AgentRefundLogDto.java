package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.Date;

/** Agent 专用退餐记录，不包含退款金额。 */
@Data
public class AgentRefundLogDto {
    /** 退餐记录 ID。 */ private Long refundId;
    /** 客户 ID。 */ private Long customerId;
    /** 订单 ID。 */ private Long orderId;
    /** 退早餐数。 */ private Integer refundBreakfastCount;
    /** 退午晚餐数。 */ private Integer refundLunchDinnerCount;
    /** 已核销但不退的早餐数。 */ private Integer verifiedBreakfastCount;
    /** 已核销但不退的午晚餐数。 */ private Integer verifiedLunchDinnerCount;
    /** 限长的退餐原因。 */ private String refundReason;
    /** 操作时间。 */ private Date operateTime;
}
