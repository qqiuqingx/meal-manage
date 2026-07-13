package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Agent 客户候选菜只读预览，不表示正式排餐生成结果。 */
@Data
public class AgentDishCandidatePreviewDto {
    /** 客户是否存在。 */ private boolean present;
    /** 客户稳定 ID。 */ private Long customerId;
    /** 客户业务编号。 */ private String customerCode;
    /** 查询排餐日期。 */ private String recordDate;
    /** 查询餐次代码。 */ private String mealTypeCode;
    /** 当前进行中订单关联的父套餐 ID。 */ private List<Long> parentPackageIds = new ArrayList<>();
    /** 当日排期及固定米饭候选总数。 */ private int totalCandidateCount;
    /** 过滤后可用候选数。 */ private int availableCandidateCount;
    /** 因套餐、排除菜或过敏过滤的候选数。 */ private int filteredCandidateCount;
    /** 最多 20 条候选预览。 */ private List<AgentDishCandidateItemDto> items = new ArrayList<>();
    /** 候选预览是否截断。 */ private boolean truncated;
}
