package me.zhengjie.modules.meal.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 菜品标签字典实体。
 */
@Getter
@Setter
@TableName("dish_tag")
public class DishTag implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜品标签ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 标签名称，在菜品标签字典内唯一。 */
    @TableField("name")
    private String name;

    /** 标签创建时间。 */
    @TableField("create_time")
    private Timestamp createTime;

    /** 标签最近更新时间。 */
    @TableField("update_time")
    private Timestamp updateTime;
}
