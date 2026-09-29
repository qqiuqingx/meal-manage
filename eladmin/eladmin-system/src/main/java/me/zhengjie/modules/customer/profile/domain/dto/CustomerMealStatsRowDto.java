package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 客户用餐统计页的单笔订单行；客户共享资料按当前档案值重复展示。
 */
@Data
public class CustomerMealStatsRowDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 列表行和排餐日历的订单身份。 */
    private Long orderId;
    private Long customerId;
    private String orderCode;

    /** 客户共享资料。 */
    private String phone;
    private String addressText;
    private String customerCode;
    private String customerName;
    private String specialRequirements;
    private String medicalRequirements;
    private String postoperativeInfo;
    private List<CustomerDietItemDto> dishRequirements;
    private List<String> dishRequirementsRaw;
    private List<String> allergyTags;
    private List<CustomerDietItemDto> dietaryRestrictions;
    private List<String> dietaryRestrictionsRaw;

    /** 当前订单资料。 */
    private String scheduleMode;
    private String scheduleModeText;
    private String mealType;
    private String mealTypeText;
    private String specification;
    private Integer soupCount;
    private Integer breakfastCount;
    private Integer lunchDinnerCount;
    private Integer totalCount;
    private Integer verifiedCount;
    private Integer scheduledCount;
    private Integer remainingCount;
    private Integer estimatedRemainingCount;
    private Integer status;
    private String statusLabel;
    private String dealTime;
    private String customMenuImage;
}
