package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 活跃客户集合的受控餐数余额明细响应。 */
@Data
public class AgentActiveCustomerBalanceResponse {
    /** 固定业务口径标识。 */
    private String metricDefinitionId = "AGENT_ACTIVE_CUSTOMER_BALANCE_DETAIL_V1";
    /** 授权范围内符合活跃口径的客户总数。 */
    private long total;
    /** 当前页脱敏明细。 */
    private List<AgentActiveCustomerBalanceItem> items = new ArrayList<>();
    /** 当前页页码。 */
    private int page;
    /** 当前页条数。 */
    private int size;
    /** 是否还有未返回的明细。 */
    private boolean truncated;
    /** 查询完成时间。 */
    private String queriedAt;
    /** 统计时区。 */
    private String timezone = "Asia/Shanghai";
}
