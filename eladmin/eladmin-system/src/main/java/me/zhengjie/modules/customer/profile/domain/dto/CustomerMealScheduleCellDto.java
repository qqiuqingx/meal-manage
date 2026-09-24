package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户排餐日历中一个订单、日期和餐次的数量单元格。
 */
@Data
public class CustomerMealScheduleCellDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单ID */
    private Long orderId;

    /** 排餐日期，格式 yyyy-MM-dd */
    private String date;

    /** 餐次：LUNCH/DINNER */
    private String mealType;

    /** 未应用排除和人工覆盖时的基础份数 */
    private Integer baseQuantity;

    /** 当前计划目标份数 */
    private Integer quantity;

    /** 目标份数中含汤的份数；为空表示沿用订单配置 */
    private Integer soupQuantity;

    /** 订单未指定含汤份数时，每份是否按订单汤品配置含汤 */
    private Boolean defaultIncludesSoup;

    /** 当前已生成的有效排餐份数 */
    private Integer generatedCount;

    /** 当前生成失败、可重试的排餐份数 */
    private Integer failedCount;

    /** 当前已核销份数 */
    private Integer verifiedCount;

    /** 是否存在人工数量覆盖 */
    private Boolean manualOverride;

    /** 是否被客户排除日期覆盖 */
    private Boolean excluded;
}
