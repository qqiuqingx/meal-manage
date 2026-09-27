package me.zhengjie.modules.meal.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 配料标签字典实体。
 */
@Getter
@Setter
@TableName("dish_ingredient_tag")
public class DishIngredientTag implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标签主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "标签ID")
    private Integer id;

    /** 标签显示名称，在数据库中唯一且不区分英文大小写。 */
    @ApiModelProperty(value = "标签名称")
    private String name;

    /** 标签创建时间。 */
    @ApiModelProperty(value = "创建时间")
    private Timestamp createTime;

    /** 标签最近更新时间。 */
    @ApiModelProperty(value = "更新时间")
    private Timestamp updateTime;
}
