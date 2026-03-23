package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;

/**
 * 当天客户总数按套餐和餐次分组统计
 * @author qqx
 * @date 2026-03-23
 **/
@Data
public class DailyCustomerStats {

    @ApiModelProperty(value = "日期")
    private String date;

    @ApiModelProperty(value = "当天总客户数（午餐+晚餐去重）")
    private Integer totalCustomerCount;

    @ApiModelProperty(value = "按餐次+套餐分组列表")
    private List<MealPackageGroup> groups;

    @Data
    public static class MealPackageGroup {
        @ApiModelProperty(value = "餐次")
        private String mealType;

        @ApiModelProperty(value = "套餐代码")
        private String mealPackage;

        @ApiModelProperty(value = "套餐名称")
        private String mealPackageDesc;

        @ApiModelProperty(value = "客户数")
        private Integer customerCount;
    }
}
