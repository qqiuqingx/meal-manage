package me.zhengjie.modules.meal.domain;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.sql.Timestamp;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 菜品配料关联实体
 * @author qqx
 * @date 2026-03-15
 **/
@Data
@TableName("dish_ingredient_relation")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DishIngredientRelation implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Integer id;

    @ApiModelProperty(value = "菜品ID")
    private Integer dishId;

    @ApiModelProperty(value = "配料ID")
    private Integer ingredientId;

    @ApiModelProperty(value = "配料名称")
    private String ingredientName;

    @ApiModelProperty(value = "单位")
    private String unit;

    @ApiModelProperty(value = "用量")
    private Double quantity;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "创建时间")
    private Timestamp createTime;
}
