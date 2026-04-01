package me.zhengjie.modules.customer.profile.domain.dto;

import io.swagger.annotations.ApiModelProperty;
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

    @ApiModelProperty(value = "页码（0基）")
    private Integer page = 0;

    @ApiModelProperty(value = "每页数量")
    private Integer size = 10;
}
