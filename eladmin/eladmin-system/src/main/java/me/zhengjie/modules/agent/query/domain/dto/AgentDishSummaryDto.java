package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Agent 菜品与配料摘要，不包含菜品制作过程或任意扩展字段。 */
@Data
public class AgentDishSummaryDto {
    /** 菜品 ID。 */ private Integer dishId;
    /** 菜品名称。 */ private String dishName;
    /** 菜品类型代码。 */ private String dishTypeCode;
    /** 可展示菜品类型。 */ private String dishTypeName;
    /** 适用餐次代码。 */ private List<String> mealTypes = new ArrayList<>();
    /** 是否启用。 */ private Boolean enabled;
    /** 最多返回 20 个配料名称。 */ private List<String> ingredientNames = new ArrayList<>();
    /** 配料是否被截断。 */ private boolean ingredientsTruncated;
}
