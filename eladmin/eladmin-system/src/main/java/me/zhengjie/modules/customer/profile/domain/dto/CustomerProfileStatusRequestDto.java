package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户档案状态更新请求 DTO
 */
@Data
public class CustomerProfileStatusRequestDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean status;
}
