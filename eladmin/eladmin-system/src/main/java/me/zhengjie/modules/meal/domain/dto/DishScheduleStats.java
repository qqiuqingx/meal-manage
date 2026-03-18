package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;
import java.util.Map;

/**
 * 首页排餐统计数据
 * @author qqx
 * @date 2026-03-14
 **/
@Data
public class DishScheduleStats {

    @ApiModelProperty(value = "日期")
    private String date;

    @ApiModelProperty(value = "午餐统计")
    private MealTypeStats lunch;

    @ApiModelProperty(value = "晚餐统计")
    private MealTypeStats dinner;

    @Data
    public static class MealTypeStats {
        @ApiModelProperty(value = "客户数量")
        private Integer customerCount;

        @ApiModelProperty(value = "替换数量")
        private Integer replacedCount;

        @ApiModelProperty(value = "菜单（按类型）")
        private Map<String, DishTypeMenu> menu;
    }

    @Data
    public static class DishTypeMenu {
        @ApiModelProperty(value = "菜品ID")
        private Integer dishId;

        @ApiModelProperty(value = "菜品名称")
        private String dishName;

        @ApiModelProperty(value = "被替换次数")
        private Integer replacedCount;
    }
}
