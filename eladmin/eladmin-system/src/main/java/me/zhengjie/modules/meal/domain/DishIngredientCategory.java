package me.zhengjie.modules.meal.domain;

import lombok.Data;
import io.swagger.annotations.ApiModelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/**
 * 配料分类实体
 * @author qqx
 * @date 2026-04-24
 **/
@Data
@TableName("dish_ingredient_category")
public class DishIngredientCategory implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "分类ID")
    private Integer id;

    @ApiModelProperty(value = "父分类ID，一级分类为空")
    private Integer parentId;

    @ApiModelProperty(value = "分类名称")
    private String name;

    @ApiModelProperty(value = "层级：1一级分类，2二级分类")
    private Integer level;

    @ApiModelProperty(value = "排序")
    private Integer sort = 0;

    @ApiModelProperty(value = "是否启用")
    private Boolean enabled = true;

    @ApiModelProperty(value = "创建时间")
    private Timestamp createTime;

    @ApiModelProperty(value = "更新时间")
    private Timestamp updateTime;

    @TableField(exist = false)
    @ApiModelProperty(value = "子分类列表")
    private List<DishIngredientCategory> children;
}
