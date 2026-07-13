package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/** Agent 排餐菜品明细。 */
@Data
public class AgentMealPlanDishItemDto {
    /** 菜品 ID。 */ private Integer dishId;
    /** 菜品名称。 */ private String dishName;
    /** 菜品类型。 */ private String dishType;
    /** 是否人工替换。 */ private boolean replaced;
    /** 换菜原因代码。 */ private String replaceReason;
    /** 本次排餐是否实际命中过敏过滤。 */ private boolean allergyFiltered;
    /** 实际命中的过敏标签。 */ private java.util.List<String> allergyReasons = new java.util.ArrayList<>();
    /** 被替换前的菜品 ID。 */ private Integer originalDishId;
    /** 被替换前的菜品名称。 */ private String originalDishName;
}
