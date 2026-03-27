package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户订单查询条件
 */
@Data
public class CustomerOrderQueryCriteria implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderCode;

    private String customerName;

    private Long customerId;

    /**
     * 订单状态: 0=已取消, 1=进行中, 2=已完成
     */
    private Integer status;

    /**
     * 成交时间范围
     */
    private LocalDateTime[] dealTime;

    /**
     * 订单开始日期范围
     */
    private LocalDate[] startDate;

    /**
     * 订单结束日期范围
     */
    private LocalDate[] endDate;
}
