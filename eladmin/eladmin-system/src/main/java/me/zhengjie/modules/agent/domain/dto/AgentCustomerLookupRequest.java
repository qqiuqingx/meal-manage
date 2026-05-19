package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查客户查询请求。
 */
@Data
public class AgentCustomerLookupRequest {
    private Long customerId;
    private String customerCode;
}
