package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/** Agent 核销或退餐历史受控请求。 */
@Data
public class AgentHistoryQueryRequest {
    /** 客户 ID，可为空。 */ private Long customerId;
    /** 订单 ID，可为空。 */ private Long orderId;
    /** 开始日期（yyyy-MM-dd），可为空。 */ private String startDate;
    /** 结束日期（yyyy-MM-dd），可为空。 */ private String endDate;
    /** 餐次代码，可为空。 */ private String mealType;
    /** 返回记录数，默认 10、最大 50。 */ private Integer recentLimit = 10;
}
