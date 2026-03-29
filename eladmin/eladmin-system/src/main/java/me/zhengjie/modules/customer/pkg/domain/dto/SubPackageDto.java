package me.zhengjie.modules.customer.pkg.domain.dto;

import lombok.Data;

@Data
public class SubPackageDto {
    private Long id;
    private String subPackageCode;
    private String subPackageName;
    private Integer meatCount;
    private Integer vegCount;
    private Integer includeSoup;
    private Integer includeRice;
    private Integer status;
    private String remark;
}
