package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查客户订单查询请求。
 */
@Data
public class AgentCustomerOrdersRequest {
    private Long customerId;
    private String customerCode;
    private Integer page;
    private Integer size;
}
