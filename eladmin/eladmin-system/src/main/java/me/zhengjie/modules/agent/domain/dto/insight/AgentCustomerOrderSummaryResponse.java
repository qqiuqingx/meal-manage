package me.zhengjie.modules.agent.domain.dto.insight;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 客户订单查询响应 DTO
 * @author qqx
 * @date 2026-07-09
 */
@Getter
@Setter
public class AgentCustomerOrderSummaryResponse {

    /** 客户是否存在 */
    private boolean present = true;

    /** 客户 ID */
    private Long customerId;

    /** 客户编号 */
    private String customerCode;

    /** 客户姓名 */
    private String customerName;

    /** 订单列表 */
    private List<AgentCustomerOrderMealBalanceItem> orders;
}
