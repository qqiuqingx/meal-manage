package me.zhengjie.modules.meal.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 餐品菜单计划映射
 * @author qqx
 * @date 2026-04-13
 **/
@Data
@TableName("meal_schedule_plan")
public class MealSchedulePlan implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "周序号(1-4)")
    @TableField("week_num")
    private Integer weekNum;

    @ApiModelProperty(value = "星期几(1-7)")
    @TableField("day_of_week")
    private Integer dayOfWeek;

    @ApiModelProperty(value = "餐次")
    @TableField("meal_time")
    private String mealTime;

    @ApiModelProperty(value = "坑位分类")
    @TableField("dish_category")
    private String dishCategory;

    @ApiModelProperty(value = "菜品ID")
    @TableField("dish_id")
    private Integer dishId;

    @ApiModelProperty(value = "是否启用")
    @TableField("enabled")
    private Boolean enabled;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private Timestamp createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField("update_time")
    private Timestamp updateTime;

    @ApiModelProperty(value = "菜品详情")
    @TableField(exist = false)
    private Dish dish;
}
