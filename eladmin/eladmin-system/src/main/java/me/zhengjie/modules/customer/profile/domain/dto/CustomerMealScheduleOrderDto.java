package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 客户排餐日历中用于标识份数网格来源的订单摘要。
 */
@Data
public class CustomerMealScheduleOrderDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单ID */
    private Long orderId;

    /** 订单状态：1进行中，4暂停 */
    private Integer status;

    /** 订单餐次类型 */
    private String mealType;

    /** 订单首日餐次 */
    private String startMealType;

    /** 订单开始日期 */
    private LocalDate startDate;

    /** 订单结束日期 */
    private LocalDate endDate;

    /** 午晚餐购买份数 */
    private Integer mealCount;

    /** 扣除已核销份后的午晚餐剩余份数 */
    private Integer remainingMealCount;

    /** 早餐购买份数 */
    private Integer breakfastCount;

    /** 扣除已核销早餐后的剩余份数 */
    private Integer remainingBreakfastCount;

    /** 订单默认是否含汤 */
    private Boolean defaultIncludesSoup;
}
