package me.zhengjie.modules.agent.formdraft.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 客户订单表单草稿字段；不包含图片和服务端计算字段。 */
@Data
public class CustomerOrderDraftPayload implements Serializable {
    /** 已有客户 ID，仅新增订单草稿使用。 */
    private Long customerId;
    /** 当前数据范围内可展示的客户编号。 */
    private String customerCode;
    /** 父套餐 ID。 */
    private Long parentPackageId;
    /** 子套餐 ID。 */
    private Long childPackageId;
    /** 早餐餐数。 */
    private Integer breakfastCount;
    /** 午餐晚餐共享餐数。 */
    private Integer lunchDinnerCount;
    /** 早餐单价。 */
    private BigDecimal breakfastPrice;
    /** 午餐晚餐单价。 */
    private BigDecimal lunchDinnerPrice;
    /** 总金额。 */
    private BigDecimal totalAmount;
    /** 定金金额。 */
    private BigDecimal depositAmount;
    /** 成交金额。 */
    private BigDecimal finalAmount;
    /** 成交时间。 */
    private LocalDateTime dealTime;
    /** 首次配送时间。 */
    private LocalDateTime firstDeliveryTime;
    /** 开始日期。 */
    private LocalDate startDate;
    /** 开始餐次。 */
    private String startMealType;
    /** 结束日期。 */
    private LocalDate endDate;
    /** 餐次类型。 */
    private String mealType;
    /** 排餐模式。 */
    private String scheduleMode;
    /** 指定配送日期及餐次。 */
    private List<DeliveryDateDraft> deliveryDates;
    /** 销售渠道。 */
    private String customerSource;
    /** 是否由试餐订单转成。 */
    private Boolean trialConverted;
    /** 关联试餐订单 ID。 */
    private Long trialOrderId;
    /** 每餐主菜数量。 */
    private Integer mainDishCount;
    /** 每餐副菜数量。 */
    private Integer sideDishCount;
    /** 每餐素菜数量。 */
    private Integer vegCount;
    /** 每餐米饭数量。 */
    private Integer riceCount;
    /** 米饭类型。 */
    private String riceType;
    /** 每餐汤数量。 */
    private Integer soupCount;
    /** 订单备注。 */
    private String remark;
    /** 订单级换菜规则。 */
    private List<ReplaceRuleDraft> replaceRules;

    /** 指定配送日期草稿。 */
    @Data
    public static class DeliveryDateDraft implements Serializable {
        /** 配送日期。 */
        private LocalDate date;
        /** 当日配送餐次。 */
        private List<String> mealTypes;
    }

    /** 订单换菜规则草稿。 */
    @Data
    public static class ReplaceRuleDraft implements Serializable {
        /** 原菜品 ID。 */
        private Long sourceDishId;
        /** 目标菜品 ID。 */
        private Long targetDishId;
        /** 是否启用规则。 */
        private Boolean enabled;
        /** 换菜备注。 */
        private String remark;
    }
}
