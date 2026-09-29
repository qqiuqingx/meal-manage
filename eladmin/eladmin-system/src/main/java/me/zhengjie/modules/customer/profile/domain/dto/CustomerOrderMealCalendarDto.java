package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 单笔订单在指定月份的排餐日历及编辑摘要。
 */
@Data
public class CustomerOrderMealCalendarDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;
    private Long customerId;
    private String orderCode;
    private String customerCode;
    private String customerName;
    private String statsMonth;
    private Integer status;
    private String mealType;
    private String startMealType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer breakfastCount;
    private Integer lunchDinnerCount;
    /** 当前月份可供计划使用的早餐数量上限，已扣除其他月份占用。 */
    private Integer availableBreakfastCount;
    /** 当前月份可供计划使用的午晚餐数量上限，已扣除导入核销及其他月份占用。 */
    private Integer availableLunchDinnerCount;
    private Boolean defaultIncludesSoup;
    private Boolean editable;
    private String readOnlyReason;
    private String revision;
    private List<CustomerMealScheduleCellDto> cells = new ArrayList<>();
    private List<CustomerOrderMealCalendarOverrideDto> overrides = new ArrayList<>();
}
