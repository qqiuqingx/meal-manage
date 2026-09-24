package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户排餐日历调整结果。
 */
@Data
public class CustomerMealScheduleAdjustmentResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 排除日期餐次数量
     */
    private Integer excludedMealCount;

    /**
     * 人工新增餐次数量
     */
    private Integer additionMealCount;

    /**
     * 因排除日期或份数下调而软删除的未核销结果行数
     */
    private Integer deletedUnverifiedPlanCount;
}
