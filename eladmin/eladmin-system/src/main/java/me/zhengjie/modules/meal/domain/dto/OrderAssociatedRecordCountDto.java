package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;

/**
 * 按订单聚合的关联业务记录数量。
 *
 * 仅承载订单标识和记录条数，可被核销、退餐等只读聚合查询复用。
 */
@Data
public class OrderAssociatedRecordCountDto {

    /** 关联订单 ID。 */
    private Long orderId;

    /** 关联记录数量。 */
    private Integer recordCount;
}
