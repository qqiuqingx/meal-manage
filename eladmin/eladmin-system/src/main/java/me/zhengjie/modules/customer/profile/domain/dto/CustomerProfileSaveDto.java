package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRuleDto;

import java.io.Serializable;
import java.util.List;

/**
 * 客户档案保存 DTO
 */
@Data
public class CustomerProfileSaveDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String customerCode;

    private String customerName;

    private String phone;

    private Integer gestationalWeek;

    private List<String> allergyTags;

    /**
     * 排除菜品ID列表
     */
    private List<Integer> excludedDishIds;

    /**
     * 排除日期列表（日期+餐次组合，Wave 1/2 完整实现）
     */
    private java.util.List<ExcludedDateDto> excludedDates;

    private String medicalRequirements;

    /**
     * 特殊要求
     */
    private String specialRequirements;

    /**
     * 生产日期（格式：yyyy-MM-dd）
     */
    private String productionDate;

    // 备注
    private String remark;

    private List<AddressDto> addresses;

    /**
     * 首单信息（仅创建时使用）
     */
    private OrderInfoDto orderInfo;

    /**
     * 地址 DTO
     */
    @Data
    public static class AddressDto implements Serializable {
        private String addressType;
        private String addressDetail;
        private String contactName;
        private String contactPhone;
    }

    /**
     * 首单信息 DTO
     */
    @Data
    public static class OrderInfoDto implements Serializable {
        private Long parentPackageId;
        private Long childPackageId;
        private Integer breakfastCount;
        private Integer lunchDinnerCount;
        private Integer totalCount;
        private java.math.BigDecimal breakfastPrice;
        private java.math.BigDecimal lunchDinnerPrice;
        private java.math.BigDecimal totalAmount;
        private java.math.BigDecimal depositAmount;
        private java.math.BigDecimal finalAmount;
        private String scheduleMode;
        private String startDate;
        private String startMealType;
        private String endDate;
        private String mealType;
        private String customerSource;
        private String deliveryDates;
        private Integer mainDishCount;
        private Integer sideDishCount;
        private Integer vegCount;
        private Integer riceCount;
        private String riceType;
        private Integer soupCount;
        private List<CustomerOrderReplaceRuleDto> replaceRules;
    }
}
