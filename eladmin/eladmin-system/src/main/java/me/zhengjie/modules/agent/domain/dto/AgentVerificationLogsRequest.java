package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查核销日志查询请求。
 */
@Data
public class AgentVerificationLogsRequest {
    private Long customerId;
    private String customerCode;
    private Long orderId;
    private String recordDateStart;
    private String recordDateEnd;
    private String mealType;
}
