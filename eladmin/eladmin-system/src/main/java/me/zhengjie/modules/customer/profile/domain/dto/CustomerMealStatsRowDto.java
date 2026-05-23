package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

/**
 * 客户用餐统计行
 */
@Data
public class CustomerMealStatsRowDto {

    private String rowKey;

    private Long customerId;

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

    private String mealBucket;

    private Boolean firstRowInGroup;

    private Integer groupRowSpan;
}
