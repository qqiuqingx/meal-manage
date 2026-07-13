package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Agent 客户订单列表受控请求。
 */
@Data
public class AgentOrderListRequest {
    /** 客户 ID。 */
    @NotNull
    private Long customerId;
    /** 订单状态，可为空。 */
    private Integer status;
    /** 从 1 开始的页码。 */
    private Integer page = 1;
    /** 单页条数，服务端最大限制为 20。 */
    private Integer size = 10;
}
