package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户已确认的饮食对象引用。
 */
@Data
public class CustomerDietItemDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对象类型：DISH、INGREDIENT、INGREDIENT_TAG、INGREDIENT_CATEGORY 或 DISH_TAG。 */
    private String type;

    /** 对象在对应字典表中的主键。 */
    private Long id;

    /** 匹配或确认时的字典名称快照。 */
    private String name;
}
