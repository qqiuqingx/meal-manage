package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;
import me.zhengjie.modules.customer.profile.util.CustomerMealStatsScheduleUtil;

import java.util.List;

/**
 * 客户用餐统计行
 */
@Data
public class CustomerMealStatsRowDto {

    private String rowKey;

    private Long customerId;

    /**
     * 当前餐池默认用于人工新增排餐的订单ID。
     */
    private Long orderId;

    private String customerCode;

    private String phone;

    private String addressText;

    private String remarkInfo;

    private String specialRequirementText;

    private String soupLabel;

    private String deliveryInfo;

    private String purchaseDateText;

    private String startDateText;

    private Integer mealCount;

    private Integer remainingMealCount;

    private List<CustomerMealStatsScheduleUtil.ScheduleDay> scheduleDays;

    /**
     * 不应用排除日期的基础应排餐日期，用于日历编辑恢复。
     */
    private List<CustomerMealStatsScheduleUtil.ScheduleDay> baseScheduleDays;

    private List<CustomerMealStatsScheduleUtil.ScheduleDay> customerScheduleDays;

    private String mealBucket;

    private Boolean firstRowInGroup;

    private Integer groupRowSpan;
}
