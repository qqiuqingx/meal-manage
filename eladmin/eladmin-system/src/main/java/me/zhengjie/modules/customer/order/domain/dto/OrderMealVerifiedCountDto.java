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
}
