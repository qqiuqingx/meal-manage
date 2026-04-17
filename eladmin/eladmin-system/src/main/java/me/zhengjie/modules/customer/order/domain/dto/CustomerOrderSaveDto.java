package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户订单保存 DTO
 */
@Data
public class CustomerOrderSaveDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 客户ID
     */
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /**
     * 父套餐ID
     */
    private Long parentPackageId;

    /**
     * 子套餐ID
     */
    private Long childPackageId;

    /**
     * 订单编号
     */
    private String orderCode;

    /**
     * 定金金额
     */
    @DecimalMin(value = "0", message = "定金金额不能为负数")
    private BigDecimal depositAmount;

    /**
     * 总金额
     */
    @NotNull(message = "总金额不能为空")
    @DecimalMin(value = "0", message = "总金额不能为负数")
    private BigDecimal totalAmount;

    /**
     * 成交金额
     */
    @NotNull(message = "成交金额不能为空")
    @DecimalMin(value = "0", message = "成交金额不能为负数")
    private BigDecimal finalAmount;

    /**
     * 早餐合计餐数
     */
    @Min(value = 0, message = "早餐餐数不能为负数")
    private Integer breakfastCount;

    /**
     * 午餐+晚餐合计餐数
     */
    @Min(value = 0, message = "午餐+晚餐餐数不能为负数")
    private Integer lunchDinnerCount;

    /**
     * 早餐单价
     */
    @DecimalMin(value = "0", message = "早餐单价不能为负数")
    private BigDecimal breakfastPrice;

    /**
     * 午餐晚餐单价
     */
    @DecimalMin(value = "0", message = "午餐晚餐单价不能为负数")
    private BigDecimal lunchDinnerPrice;

    /**
     * 核销餐数(合计)
     */
    @Min(value = 0, message = "核销餐数不能为负数")
    private Integer verifiedCount;

    /**
     * 核销金额
     */
    @DecimalMin(value = "0", message = "核销金额不能为负数")
    private BigDecimal verifiedAmount;

    /**
     * 餐费余额（后端计算）
     */
    private BigDecimal mealBalance;

    /**
     * 剩余餐数（后端计算）
     */
    private Integer remainingCount;

    /**
     * 成交时间
     */
    private LocalDateTime dealTime;

    /**
     * 第一次送餐时间
     */
    private LocalDateTime firstDeliveryTime;

    /**
     * 订单开始日期
     */
    private LocalDate startDate;

    /**
     * 订单结束日期
     */
    private LocalDate endDate;

    /**
     * 订单状态(0=已取消,1=进行中,2=已完成)
     */
    private Integer status;

    /**
     * 餐次类型(LUNCH=午餐订单,DINNER=晚餐订单,ALL=全餐次订单)
     */
    private String mealType;

    /**
     * 排餐模式(SCHEDULE=指定日期送,DAILY=每天送,WEEKEND=周末送,WEEKDAY=工作日送)
     */
    private String scheduleMode;

    /**
     * 送餐日期(JSON数组格式,如["2026-04-01","2026-04-03"])
     */
    private String deliveryDates;

    /**
     * 送餐日期及餐次（新格式，供前端日历使用）
     * 格式：[{"date":"2026-04-15","mealTypes":["BREAKFAST","LUNCH","DINNER"]},...]
     */
    private String deliveryDatesWithMealTypes;

    /**
     * 备注
     */
    private String remark;

    /**
     * 销售渠道
     */
    private String customerSource;

    /**
     * 每餐主菜/荤菜数量
     */
    @NotNull(message = "主菜数量不能为空")
    @Min(value = 0, message = "主菜数量不能为负数")
    @Max(value = 10, message = "主菜数量不能超过10份")
    private Integer mainDishCount;

    /**
     * 每餐副菜数量
     */
    @NotNull(message = "副菜数量不能为空")
    @Min(value = 0, message = "副菜数量不能为负数")
    @Max(value = 10, message = "副菜数量不能超过10份")
    private Integer sideDishCount;

    /**
     * 每餐素菜数量
     */
    @NotNull(message = "素菜数量不能为空")
    @Min(value = 0, message = "素菜数量不能为负数")
    @Max(value = 10, message = "素菜数量不能超过10份")
    private Integer vegCount;

    /**
     * 每餐米饭数量
     */
    @NotNull(message = "米饭数量不能为空")
    @Min(value = 0, message = "米饭数量不能为负数")
    @Max(value = 10, message = "米饭数量不能超过10份")
    private Integer riceCount;

    /**
     * 每餐汤数量
     */
    @NotNull(message = "汤数量不能为空")
    @Min(value = 0, message = "汤数量不能为负数")
    @Max(value = 10, message = "汤数量不能超过10份")
    private Integer soupCount;
}
