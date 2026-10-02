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
     * 统计月份，格式 yyyy-MM；筛选次月前开始的剩余订单，或该月有历史导入数量的订单。
     */
    private String statsMonth;
}
