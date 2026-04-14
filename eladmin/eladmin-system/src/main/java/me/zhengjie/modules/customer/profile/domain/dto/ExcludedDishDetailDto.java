package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 排除菜品详情 DTO
 * 用于查询客户排除菜品的详细信息，包括菜品ID和匹配状态
 */
@Data
public class ExcludedDishDetailDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 客户档案ID
     */
    private Long customerId;

    /**
     * 客户编号
     */
    private String customerCode;

    /**
     * 客户姓名
     */
    private String customerName;

    /**
     * 排除菜品ID列表(JSON格式)
     */
    private String excludedDishIds;

    /**
     * 菜品匹配状态
     * true: 菜品存在于系统中且可被排除
     * false: 菜品不存在或已失效
     */
    private Boolean dishMatch;
}