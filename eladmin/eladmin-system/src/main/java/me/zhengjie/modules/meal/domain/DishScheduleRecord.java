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
 * 每日排餐记录
 * @author qqx
 * @date 2026-03-14
 **/
@Data
@TableName("dish_schedule_record")
public class DishScheduleRecord implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Integer id;

    @ApiModelProperty(value = "排餐日期")
    @TableField("record_date")
    private String recordDate;

    @ApiModelProperty(value = "餐次：LUNCH午餐、DINNER晚餐")
    @TableField("meal_type")
    private String mealType;

    @ApiModelProperty(value = "周数")
    @TableField("week_num")
    private Integer weekNum;

    @ApiModelProperty(value = "星期（1-7）")
    @TableField("day_of_week")
    private Integer dayOfWeek;

    @ApiModelProperty(value = "客户数量")
    @TableField("customer_count")
    private Integer customerCount;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private Timestamp createTime;

    @ApiModelProperty(value = "是否删除：0否/1是")
    @TableField("deleted")
    private Boolean deleted;
}
