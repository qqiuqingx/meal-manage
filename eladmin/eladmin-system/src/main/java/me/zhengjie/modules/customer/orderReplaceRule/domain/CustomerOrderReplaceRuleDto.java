package me.zhengjie.modules.customer.orderReplaceRule.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单换菜规则 DTO
 * @author qqx
 * @date 2026-05-06
 **/
@Data
public class CustomerOrderReplaceRuleDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private Long sourceDishId;

    private String sourceDishName;

    private String sourceDishType;

    private Long targetDishId;

    private String targetDishName;

    private String targetDishType;

    private Boolean enabled;

    private String remark;

    /**
     * 前端展示用：规则引用的菜品是否已失效（不存在或已停用）
     */
    private Boolean sourceDishInvalid;

    private Boolean targetDishInvalid;
}
