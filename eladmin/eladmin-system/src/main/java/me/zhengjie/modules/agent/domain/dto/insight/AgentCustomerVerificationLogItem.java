package me.zhengjie.modules.agent.domain.dto.insight;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 客户核销记录明细项
 * @author qqx
 * @date 2026-07-09
 */
@Getter
@Setter
public class AgentCustomerVerificationLogItem {

    /** 核销记录 ID */
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 餐次：BREAKFAST / LUNCH / DINNER */
    private String mealType;

    /** 核销数量 */
    private Integer verificationCount;

    /** 核销日期 */
    private Date recordDate;

    /** 核销时间 */
    private Date createTime;

    /** 是否已删除 */
    private Integer deleted;
}
