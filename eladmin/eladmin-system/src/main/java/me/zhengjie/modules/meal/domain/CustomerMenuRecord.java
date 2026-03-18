package me.zhengjie.modules.meal.domain;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.sql.Timestamp;
import java.io.Serializable;
import io.swagger.annotations.ApiModelProperty;

/**
 * 客户菜单记录
 * @author qqx
 * @date 2026-03-14
 **/
@Data
@TableName("customer_menu_record")
public class CustomerMenuRecord implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Integer id;

    @ApiModelProperty(value = "关联排餐记录ID")
    @TableField("record_id")
    private Integer recordId;

    @ApiModelProperty(value = "客户ID")
    @TableField("customer_id")
    private Integer customerId;

    @ApiModelProperty(value = "客户名称")
    @TableField("customer_name")
    private String customerName;

    @ApiModelProperty(value = "菜品类型")
    @TableField("dish_type")
    private String dishType;

    @ApiModelProperty(value = "菜品ID")
    @TableField("dish_id")
    private Integer dishId;

    @ApiModelProperty(value = "菜品名称")
    @TableField("dish_name")
    private String dishName;

    @ApiModelProperty(value = "配料")
    @TableField("dish_ingredients")
    private String dishIngredients;

    @ApiModelProperty(value = "是否被替换（0否/1是）")
    @TableField("is_replaced")
    private Boolean isReplaced;

    @ApiModelProperty(value = "原菜品ID")
    @TableField("original_dish_id")
    private Integer originalDishId;

    @ApiModelProperty(value = "替换原因")
    @TableField("replacement_reason")
    private String replacementReason;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private Timestamp createTime;

    @ApiModelProperty(value = "是否删除：0否/1是")
    @TableField("deleted")
    private Boolean deleted;
}
