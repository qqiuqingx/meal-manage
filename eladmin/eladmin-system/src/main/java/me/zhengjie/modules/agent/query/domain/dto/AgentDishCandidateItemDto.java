package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Agent 候选菜预览项，标明当前客户是否可用及受控过滤原因。 */
@Data
public class AgentDishCandidateItemDto {
    /** 菜品稳定 ID。 */ private Integer dishId;
    /** 菜品名称。 */ private String dishName;
    /** 菜品类型代码。 */ private String dishTypeCode;
    /** 最多 20 个配料名称。 */ private List<String> ingredientNames = new ArrayList<>();
    /** 配料名称是否被截断。 */ private boolean ingredientsTruncated;
    /** 是否可作为该客户的候选菜。 */ private boolean available;
    /** 过滤原因，仅包含套餐、排除菜或过敏标签等稳定摘要。 */ private List<String> filterReasons = new ArrayList<>();
}
