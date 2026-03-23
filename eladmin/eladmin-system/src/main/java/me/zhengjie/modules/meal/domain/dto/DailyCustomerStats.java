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

    @ApiModelProperty(value = "按来源分组列表")
    private List<SourceGroup> sourceGroups;

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

    @Data
    public static class SourceGroup {
        @ApiModelProperty(value = "来源")
        private String source;

        @ApiModelProperty(value = "来源名称")
        private String sourceDesc;

        @ApiModelProperty(value = "客户数")
        private Integer customerCount;
    }
}
