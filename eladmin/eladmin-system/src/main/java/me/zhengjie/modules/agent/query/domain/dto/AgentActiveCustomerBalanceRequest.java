package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/** 活跃客户餐数余额明细的受控分页请求。 */
@Data
public class AgentActiveCustomerBalanceRequest {
    /** 页码，从 1 开始。 */
    private Integer page = 1;
    /** 每页条数，最大 50。 */
    private Integer size = 50;
}
