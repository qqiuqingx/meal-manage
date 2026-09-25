package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户批量导入中一格「日期 + 餐次」的数量草稿。
 *
 * <p>对应排餐日历的逐餐数量契约：{@code quantity} 为目标份数，{@code soupQuantity}
 * 为其中含汤份数（为空表示沿用订单汤品配置）。早餐格不进入导入。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class CustomerImportMealCellDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据来源的源工作表行号（1 基）
     */
    private Integer sourceRow;

    /**
     * 排餐日期，格式 yyyy-MM-dd
     */
    private String date;

    /**
     * 餐次：LUNCH / DINNER
     */
    private String mealType;

    /**
     * 目标配送份数
     */
    private Integer quantity;

    /**
     * 目标份数中含汤的份数；为空表示沿用订单汤品配置
     */
    private Integer soupQuantity;
}
