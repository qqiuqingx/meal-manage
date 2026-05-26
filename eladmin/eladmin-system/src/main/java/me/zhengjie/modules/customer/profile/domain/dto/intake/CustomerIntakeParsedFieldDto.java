package me.zhengjie.modules.customer.profile.domain.dto.intake;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户话术解析字段轨迹，方便前端展示原文到系统字段的映射。
 */
@Data
public class CustomerIntakeParsedFieldDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 原始标签，如 联系人、电话、套餐。
     */
    private String label;

    /**
     * 原始字段值。
     */
    private String rawValue;

    /**
     * 目标字段路径，如 customerName。
     */
    private String targetField;

    /**
     * 规范化后的值。
     */
    private Object normalizedValue;
}
