package me.zhengjie.modules.agent.domain.dto.insight;

import lombok.Getter;
import lombok.Setter;

/**
 * 客户信息查询请求 DTO
 * @author qqx
 * @date 2026-07-09
 */
@Getter
@Setter
public class AgentCustomerInsightRequest {

    /** 客户 ID */
    private Long customerId;

    /** 客户编号 */
    private String customerCode;

    /** 开始日期（含），格式 yyyy-MM-dd */
    private String recordDateStart;

    /** 结束日期（含），格式 yyyy-MM-dd */
    private String recordDateEnd;

    /** 餐次：BREAKFAST / LUNCH / DINNER / LUNCH_DINNER */
    private String mealType;

    /** 订单状态筛选：1=进行中 */
    private Integer orderStatus;

    /** 最近记录条数限制 */
    private Integer recentLimit;
}
