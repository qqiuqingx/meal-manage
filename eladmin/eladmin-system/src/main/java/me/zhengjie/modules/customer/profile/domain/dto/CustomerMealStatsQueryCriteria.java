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
}
