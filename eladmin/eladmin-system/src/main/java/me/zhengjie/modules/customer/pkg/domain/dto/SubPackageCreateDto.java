package me.zhengjie.modules.customer.pkg.domain.dto;

import lombok.Data;

@Data
public class SubPackageCreateDto {
    private String subPackageCode;
    private String subPackageName;
    private Integer meatCount;
    private Integer vegCount;
    private Integer includeSoup;
    private Integer includeRice;
    private String remark;
    private Long parentPackageId;  // 所属父套餐
}
