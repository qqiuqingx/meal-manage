package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户订单详情 DTO
 */
@Data
public class CustomerOrderDetailDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long customerId;

    private String customerName;

    private String phone;

    private String orderCode;

    private BigDecimal depositAmount;

    private BigDecimal totalAmount;

    private BigDecimal finalAmount;

    private Integer breakfastCount;

    private Integer lunchDinnerCount;

    private Integer totalCount;

    private BigDecimal breakfastPrice;

    private BigDecimal lunchDinnerPrice;

    private Integer verifiedCount;

    private BigDecimal verifiedAmount;

    private BigDecimal mealBalance;

    private Integer remainingCount;

    private LocalDateTime dealTime;

    private LocalDateTime firstDeliveryTime;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer status;

    private String statusDesc;

    private String remark;

    private String customerSource;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
