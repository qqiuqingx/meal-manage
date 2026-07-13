package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/** Agent 活跃客户或即将到期订单的受控日期范围条件。 */
@Data
public class AgentOperationOrderRequest {
    /** 范围开始日期，格式 yyyy-MM-dd。 */
    private String startDate;
    /** 范围结束日期，格式 yyyy-MM-dd。 */
    private String endDate;
}
