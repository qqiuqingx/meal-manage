package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

/**
 * 客户档案查询条件
 */
@Data
public class CustomerProfileQueryCriteria {

    private String customerCode;
    private String customerName;
    private String phone;
    private Long parentPackageId;
    private Long childPackageId;
    private Boolean status;
}