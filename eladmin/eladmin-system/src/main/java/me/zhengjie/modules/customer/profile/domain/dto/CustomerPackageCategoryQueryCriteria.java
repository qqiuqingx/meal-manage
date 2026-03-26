package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

/**
 * 套餐分类查询条件
 */
@Data
public class CustomerPackageCategoryQueryCriteria {

    private String categoryName;
    private String categoryCode;
    private Long parentId;
    private Integer level;
    private Boolean enabled;
}
