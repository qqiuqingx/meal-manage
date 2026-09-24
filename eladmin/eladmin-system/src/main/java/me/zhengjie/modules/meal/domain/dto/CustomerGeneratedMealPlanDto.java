package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 客户指定日期餐次的已生成排餐记录。
 */
@Data
public class CustomerGeneratedMealPlanDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 排餐主表ID
     */
    private Long mealPlanId;

    /**
     * 客户排餐记录ID
     */
    private Long customerPlanId;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 关联订单ID
     */
    private Long orderId;

    /**
     * 配送份序号
     */
    private Integer servingNo;

    /**
     * 排餐结果状态：0失败/1成功
     */
    private Integer status;

    /**
     * 排餐日期
     */
    private LocalDate recordDate;

    /**
     * 餐次
     */
    private String mealType;

    /**
     * 是否已核销
     */
    private Boolean verified;
}
