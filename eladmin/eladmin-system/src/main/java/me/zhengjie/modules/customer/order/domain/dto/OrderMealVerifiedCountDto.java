package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Data;

/**
 * 批量统计订单各餐次已核销数 DTO
 */
@Data
public class OrderMealVerifiedCountDto {

    private Long orderId;

    private String mealType;

    private Integer verifiedCount;

    /** 按核销日志日期分组时的送餐日期；仅按餐次汇总时为空。 */
    private java.time.LocalDate recordDate;
}
