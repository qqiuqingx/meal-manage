package me.zhengjie.modules.customer.package.domain.dto;

import lombok.Data;

@Data
public class ParentPackageQueryCriteria {

    private String packageName;
    private String packageCode;
    private String prefix;
    private Integer status;
}
