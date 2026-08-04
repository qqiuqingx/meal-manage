package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/**
 * Agent 订单列表受控请求。
 */
@Data
public class AgentOrderListRequest {
    /** 客户 ID；为空时仅允许在当前签名数据范围内执行受控分页查询。 */
    private Long customerId;
    /** 订单状态，可为空。 */
    private Integer status;
    /** 从 1 开始的页码。 */
    private Integer page = 1;
    /** 单页条数，服务端最大限制为 20。 */
    private Integer size = 10;
}
