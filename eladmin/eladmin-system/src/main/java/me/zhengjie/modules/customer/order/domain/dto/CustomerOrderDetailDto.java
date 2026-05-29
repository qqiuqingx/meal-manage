package me.zhengjie.modules.customer.order.domain.dto;

import lombok.Data;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRuleDto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户订单详情 DTO
 */
@Data
public class CustomerOrderDetailDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long customerId;

    private String customerName;

    private String phone;

    private Long parentPackageId;

    private Long childPackageId;

    private String parentPackageName;

    private String childPackageName;

    private String orderCode;

    private BigDecimal depositAmount;

    private BigDecimal totalAmount;

    private BigDecimal finalAmount;

    private Integer breakfastCount;

    private Integer lunchDinnerCount;

    private Integer totalCount;

    private BigDecimal breakfastPrice;

    private BigDecimal lunchDinnerPrice;

    private Integer verifiedCount;

    private BigDecimal verifiedAmount;

    private BigDecimal mealBalance;

    private Integer remainingCount;

    private LocalDateTime dealTime;

    private LocalDateTime firstDeliveryTime;

    private LocalDate startDate;

    private String startMealType;

    private LocalDate endDate;

    private Integer status;

    private String statusDesc;

    private String mealType;

    private String mealTypeDesc;

    private String scheduleMode;

    private String deliveryDates;

    private String remark;

    private String specialRequirements;

    private List<String> allergyTags;

    private String customerSource;

    private Boolean trialConverted;

    private Long trialOrderId;

    private String trialOrderCode;

    private Integer mainDishCount;

    private Integer sideDishCount;

    private Integer vegCount;

    private Integer riceCount;

    private String riceType;

    private Integer soupCount;

    /**
     * 自定义菜单图片地址，用于客户换菜参考
     */
    private String customMenuImage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 换菜规则列表
     */
    private List<CustomerOrderReplaceRuleDto> replaceRules;
}
