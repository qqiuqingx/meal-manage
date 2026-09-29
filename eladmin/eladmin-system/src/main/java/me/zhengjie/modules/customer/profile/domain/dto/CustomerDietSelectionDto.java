package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 确认导入时对一个歧义饮食词项的人工选择。
 */
@Data
public class CustomerDietSelectionDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sourceKey;

    /** SELECT 或 SKIP。 */
    private String action;

    /** SELECT 时指定的候选类型。 */
    private String type;

    /** SELECT 时指定的候选字典ID。 */
    private Long id;
}
