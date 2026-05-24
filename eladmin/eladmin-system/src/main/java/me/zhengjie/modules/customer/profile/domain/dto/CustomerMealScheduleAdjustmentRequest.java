package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户排餐日历调整请求。
 */
@Data
public class CustomerMealScheduleAdjustmentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 客户ID
     */
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    /**
     * 页面保存后的排除日期完整列表
     */
    private List<ExcludedDateDto> excludedDates = new ArrayList<>();

    /**
     * 页面保存后的人工新增完整列表
     */
    private List<CustomerMealScheduleAdditionDto> additions = new ArrayList<>();
}
