package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/**
 * Agent 订单详情受控请求，可带当前客户关系约束。
 */
@Data
public class AgentOrderDetailRequest {
    /** 订单 ID。 */
    private Long orderId;
    /** 订单编号。 */
    private String orderCode;
    /** 当前上下文客户 ID，用于防止跨客户订单猜测。 */
    private Long customerId;
}
