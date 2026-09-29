package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 单订单排餐日历的一条完整数量覆盖。
 */
@Data
public class CustomerOrderMealCalendarOverrideDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 覆盖日期，格式 yyyy-MM-dd。 */
    @NotBlank
    private String date;

    /** 餐次：BREAKFAST、LUNCH 或 DINNER。 */
    @NotBlank
    private String mealType;

    /** 目标份数；0 表示仅停用当前订单该日餐次。 */
    @NotNull
    @Min(0)
    private Integer quantity;

    /** 含汤份数；0 份覆盖时服务端归一化为 null。 */
    @Min(0)
    private Integer soupQuantity;

    /** 调整说明。 */
    @Size(max = 255)
    private String remark;
}
