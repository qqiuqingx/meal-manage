package me.zhengjie.modules.meal.domain.dto;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;

/**
 * 排餐结果
 * @author qqx
 * @date 2026-03-14
 **/
@Data
public class DishScheduleResult {

    @ApiModelProperty(value = "日期")
    private String date;

    @ApiModelProperty(value = "周数")
    private Integer week;

    @ApiModelProperty(value = "星期")
    private Integer day;

    @ApiModelProperty(value = "菜单（按套餐分组）")
    private Map<String, MenuByPackage> menuByPackage;

    @ApiModelProperty(value = "客户菜单列表")
    private List<CustomerMenu> customers;

    /**
     * 按套餐的菜单
     */
    @Data
    public static class MenuByPackage {
        @ApiModelProperty(value = "午餐菜单")
        private Map<String, DishVO> lunch;

        @ApiModelProperty(value = "晚餐菜单")
        private Map<String, DishVO> dinner;
    }

    /**
     * 菜品VO
     */
    @Data
    public static class DishVO {
        @ApiModelProperty(value = "菜品ID")
        private Integer id;

        @ApiModelProperty(value = "菜品名称")
        private String name;

        @ApiModelProperty(value = "配料")
        private String ingredients;

        @ApiModelProperty(value = "配料列表")
        private List<DishIngredientDto> ingredientList;

        @ApiModelProperty(value = "图片路径")
        private String imageUrl;

        @ApiModelProperty(value = "是否被替换")
        private Boolean replaced;

        @ApiModelProperty(value = "原菜品ID")
        private Integer originalId;

        @ApiModelProperty(value = "替换原因")
        private String reason;

        public static DishVO fromDish(me.zhengjie.modules.meal.domain.Dish dish) {
            DishVO vo = new DishVO();
            vo.setId(dish.getId());
            vo.setName(dish.getName());
            vo.setIngredients(dish.getIngredients());
            vo.setIngredientList(dish.getIngredientList());
            vo.setImageUrl(dish.getImageUrl());
            vo.setReplaced(false);
            return vo;
        }
    }

    /**
     * 客户菜单
     */
    @Data
    public static class CustomerMenu {
        @ApiModelProperty(value = "客户ID")
        private Integer customerId;

        @ApiModelProperty(value = "客户名称")
        private String customerName;

        @ApiModelProperty(value = "套餐")
        private String mealPackage;

        @ApiModelProperty(value = "忌口")
        private List<String> restrictions;

        @ApiModelProperty(value = "客户菜单")
        private MenuByPackage menu;
    }
}
