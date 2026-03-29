package me.zhengjie.modules.customer.pkg.domain.dto;

import lombok.Data;

@Data
public class ParentPackageQueryCriteria {

    private String packageName;
    private String packageCode;
    private String prefix;
    private Integer status;
}
