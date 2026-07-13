package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.Date;

/** Agent 专用核销日志，不包含金额或单价。 */
@Data
public class AgentVerificationLogDto {
    /** 核销日志 ID。 */ private Long verificationId;
    /** 客户 ID。 */ private Long customerId;
    /** 订单 ID。 */ private Long orderId;
    /** 排餐客户记录 ID。 */ private Long mealPlanCustomerId;
    /** 排餐日期。 */ private Date recordDate;
    /** 餐次代码。 */ private String mealTypeCode;
    /** 核销餐数。 */ private Integer verificationCount;
    /** 是否已退餐。 */ private boolean refunded;
    /** 核销时间。 */ private Date operateTime;
}
