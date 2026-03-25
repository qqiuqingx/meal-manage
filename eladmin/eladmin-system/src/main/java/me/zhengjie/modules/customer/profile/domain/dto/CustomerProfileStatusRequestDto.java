package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户档案状态更新请求 DTO
 */
@Data
public class CustomerProfileStatusRequestDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标状态
     */
    private Boolean status;

    /**
     * 套餐信息(启用时必填)
     */
    private CustomerProfileSaveDto.PackageInfoDto packageInfo;
}