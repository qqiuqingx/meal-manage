package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户订单查询条件
 */
@Data
public class CustomerOrderQueryCriteria implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderCode;

    private String customerName;

    private Long customerId;

    private String customerCode;

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

    /**
     * 销售渠道
     */
    private String customerSource;

    /**
     * 排餐日期（前端传入，用于筛选该日期能参与排餐的订单）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduleDate;

    /**
     * 符合排餐条件的订单ID列表（后端Service层计算，禁止信任前端传入）
     */
    private List<Long> eligibleOrderIds;
}
