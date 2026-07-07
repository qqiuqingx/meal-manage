package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户订单有效期调整结果。
 */
@Data
public class CustomerOrderEffectiveDateAdjustResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单ID。 */
    private Long orderId;

    /** 调整前开始日期。 */
    private String oldStartDate;

    /** 调整前结束日期。 */
    private String oldEndDate;

    /** 调整后开始日期。 */
    private String newStartDate;

    /** 调整后结束日期。 */
    private String newEndDate;
}
