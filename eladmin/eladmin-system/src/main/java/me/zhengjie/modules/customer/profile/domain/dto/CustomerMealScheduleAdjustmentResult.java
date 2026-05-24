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
     * 因取消排餐而软删除的未核销客户排餐记录数
     */
    private Integer deletedUnverifiedPlanCount;
}
