package me.zhengjie.modules.customer.profile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户排餐日历人工新增记录。
 */
@Data
@TableName("customer_meal_schedule_addition")
public class CustomerMealScheduleAddition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 订单ID，用于确定套餐、餐品配置和餐数池
     */
    private Long orderId;

    /**
     * 人工新增排餐日期
     */
    private LocalDate recordDate;

    /**
     * 餐次：BREAKFAST/LUNCH/DINNER
     */
    private String mealType;

    /**
     * 该订单日期餐次的目标配送份数，历史记录缺省为一份。
     */
    private Integer quantity;

    /**
     * 目标份数中含汤的份数；为空时沿用订单汤品配置。
     */
    private Integer soupQuantity;

    /**
     * 人工新增原因或备注
     */
    private String remark;

    /**
     * 是否删除
     */
    private Boolean deleted;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
