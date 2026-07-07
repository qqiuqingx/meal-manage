package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查退餐日志查询请求。
 */
@Data
public class AgentMealRefundsRequest {
    private Long customerId;
    private String customerCode;
    private Long orderId;
}
