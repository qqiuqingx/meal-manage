package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.sql.Timestamp;

/**
 * 排餐记录查询结果
 * @author qqx
 * @date 2026-03-17
 **/
@Data
public class DishScheduleRecordVO {

    @ApiModelProperty(value = "排餐记录ID")
    private Integer recordId;

    @ApiModelProperty(value = "排餐日期")
    private String recordDate;

    @ApiModelProperty(value = "餐次（LUNCH午餐/DINNER晚餐）")
    private String mealType;

    @ApiModelProperty(value = "周数")
    private Integer weekNum;

    @ApiModelProperty(value = "星期（1-7）")
    private Integer dayOfWeek;

    @ApiModelProperty(value = "客户数量")
    private Integer customerCount;

    @ApiModelProperty(value = "客户菜单记录")
    private List<CustomerMenuVO> customerMenus;

    @ApiModelProperty(value = "创建时间")
    private Timestamp createTime;

    /**
     * 客户菜单详情
     */
    @Data
    public static class CustomerMenuVO {
        @ApiModelProperty(value = "客户菜单记录ID")
        private Integer id;

        @ApiModelProperty(value = "客户ID")
        private Integer customerId;

        @ApiModelProperty(value = "客户名称")
        private String customerName;

        @ApiModelProperty(value = "套餐类型/菜品类型")
        private String dishType;

        @ApiModelProperty(value = "菜品ID")
        private Integer dishId;

        @ApiModelProperty(value = "菜品名称")
        private String dishName;

        @ApiModelProperty(value = "配料")
        private String dishIngredients;

        @ApiModelProperty(value = "是否被替换")
        private Boolean isReplaced;

        @ApiModelProperty(value = "原菜品ID")
        private Integer originalDishId;

        @ApiModelProperty(value = "替换原因")
        private String replacementReason;
    }
}
