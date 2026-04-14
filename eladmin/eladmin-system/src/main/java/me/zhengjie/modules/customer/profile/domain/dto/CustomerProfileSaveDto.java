package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

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

    private String medicalRequirements;

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
        private String endDate;
        private String mealType;
        private String customerSource;
        private String deliveryDates;
    }
}
