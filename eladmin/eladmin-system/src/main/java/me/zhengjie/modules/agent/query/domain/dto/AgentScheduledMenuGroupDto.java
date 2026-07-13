package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 公共排期菜单中单个餐次的受控分组。 */
@Data
public class AgentScheduledMenuGroupDto {
    /** 餐次代码。 */
    private String mealTypeCode;
    /** 餐次展示名称。 */
    private String mealTypeName;
    /** 当前餐次已配置的菜品总数。 */
    private int total;
    /** 已按全局结果上限截取的菜品摘要。 */
    private List<AgentDishSummaryDto> items = new ArrayList<>();
}
