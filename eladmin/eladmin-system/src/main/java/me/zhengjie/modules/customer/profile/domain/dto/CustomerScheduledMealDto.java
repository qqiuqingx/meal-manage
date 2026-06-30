package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 客户已排餐日期餐次。
 */
@Data
public class CustomerScheduledMealDto {

    private Long customerId;

    private LocalDate recordDate;

    private String mealType;
}
