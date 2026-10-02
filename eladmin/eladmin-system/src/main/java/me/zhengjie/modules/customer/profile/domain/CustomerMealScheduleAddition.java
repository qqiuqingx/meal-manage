package me.zhengjie.modules.customer.profile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单排餐日历的日期餐次数量覆盖记录。
 */
@Data
@TableName("customer_meal_schedule_addition")
public class CustomerMealScheduleAddition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工作簿历史数量来源标识，不作为未来人工计划。 */
    public static final String IMPORTED_HISTORY_REMARK = "客户用餐计划表历史导入";

    /** 工作簿未来数量的来源标识。 */
    public static final String IMPORTED_PLAN_REMARK = "客户用餐计划表导入";

    /**
     * 判断日格是否由导入维护，避免续导夺取人工来源的数量覆盖。
     * @return 历史或未来导入来源时为 true
     */
    public boolean isImported() {
        return isImportedHistory() || IMPORTED_PLAN_REMARK.equals(remark);
    }

    /**
     * 判断数量记录是否来自客户工作簿历史格，用于只读展示及保存保护。
     *
     * @return 历史导入记录为 true
     */
    public boolean isImportedHistory() {
        return IMPORTED_HISTORY_REMARK.equals(remark);
    }

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
     * 该订单日期餐次的目标配送份数；缺省按一份处理，零份表示仅停用当前订单该日期餐次。
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
