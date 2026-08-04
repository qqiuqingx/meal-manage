package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Agent 核销或退餐历史受控请求。 */
@Data
public class AgentHistoryQueryRequest {
    /** 客户 ID，可为空。 */ @Min(1) private Long customerId;
    /** 订单 ID，可为空。 */ @Min(1) private Long orderId;
    /** 客户编号，可由统一 Agent 接口解析为客户 ID。 */ @Size(max = 64) private String customerCode;
    /** 订单编号，可由统一 Agent 接口解析为订单 ID。 */ @Size(max = 64) private String orderCode;
    /** 开始日期（yyyy-MM-dd），可为空。 */ @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String startDate;
    /** 结束日期（yyyy-MM-dd），可为空。 */ @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String endDate;
    /** 餐次代码，可为空。 */ @Pattern(regexp = "BREAKFAST|LUNCH|DINNER") private String mealType;
    /** 返回记录数，默认 10、最大 50。 */ @Min(1) @Max(50) private Integer recentLimit = 10;
    /** 从 1 开始的页码；新统一接口优先使用该字段。 */ @Min(1) private Integer page;
    /** 单页条数，最大 50；新统一接口优先使用该字段。 */ @Min(1) @Max(50) private Integer size;
}
