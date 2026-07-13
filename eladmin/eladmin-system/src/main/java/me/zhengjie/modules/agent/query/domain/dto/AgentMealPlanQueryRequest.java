package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/** Agent 客户排餐受控查询请求。 */
@Data
public class AgentMealPlanQueryRequest {
    /** 客户 ID；范围查询不允许携带该字段。 */ private Long customerId;
    /** 排餐日期（yyyy-MM-dd）。 */ private String recordDate;
    /** 排餐起始日期（yyyy-MM-dd），与结束日期组合时最多查询 31 天。 */ private String startDate;
    /** 排餐结束日期（yyyy-MM-dd），与起始日期组合时最多查询 31 天。 */ private String endDate;
    /** 餐次（BREAKFAST/LUNCH/DINNER）；跨客户单日范围为空时查询全部餐次。 */ private String mealType;
    /** 客户排餐记录 ID，提供时优先精确查询。 */ private Long customerMealPlanId;
    /** 页码，范围查询从 1 开始。 */ private Integer page;
    /** 单页条数，范围查询最大 50。 */ private Integer size;
}
