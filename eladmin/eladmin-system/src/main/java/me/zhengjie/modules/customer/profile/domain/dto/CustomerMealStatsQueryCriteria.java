package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

/**
 * 客户用餐统计查询条件
 */
@Data
public class CustomerMealStatsQueryCriteria {

    private String customerCode;

    private String customerName;

    private String phone;

    /**
     * 统计月份，格式：yyyy-MM。用于筛选开始送餐时间早于该月下一月月初的剩余订单。
     */
    private String statsMonth;
}
