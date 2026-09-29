package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 单订单排餐日历保存结果。
 */
@Data
public class CustomerOrderMealCalendarSaveResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String statsMonth;
    private String revision;
    private Integer deletedUnverifiedPlanCount;
}
