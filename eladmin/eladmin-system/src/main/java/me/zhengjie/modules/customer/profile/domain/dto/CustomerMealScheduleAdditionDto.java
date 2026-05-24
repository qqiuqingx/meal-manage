package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 客户排餐日历人工新增餐次。
 */
@Data
public class CustomerMealScheduleAdditionDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 使用的订单ID
     */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /**
     * 新增日期，格式 yyyy-MM-dd
     */
    @NotBlank(message = "日期不能为空")
    private String date;

    /**
     * 餐次：BREAKFAST/LUNCH/DINNER
     */
    @NotBlank(message = "餐次不能为空")
    private String mealType;

    /**
     * 备注
     */
    private String remark;
}
