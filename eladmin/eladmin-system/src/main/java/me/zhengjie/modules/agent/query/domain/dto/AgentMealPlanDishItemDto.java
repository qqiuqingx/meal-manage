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
}
